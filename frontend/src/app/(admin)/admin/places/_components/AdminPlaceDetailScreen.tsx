"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { adminPlaceWriteErrorMessage } from "../_lib/saveError";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { adminPlaceApi } from "../_api/adminPlaceApi";
import { adminPlaceKeys, useAdminPlace, useDeleteAdminPlace, useUpdateAdminPlace } from "../_api/queries";
import { toAdminPlaceFormValues } from "../_api/mapper";
import { diffAdminPlaceUpdate, hasAdminPlaceChanges } from "../_lib/diffAdminPlaceUpdate";
import { adminPlaceDetailHref, adminPlaceListHref } from "../_lib/filterAdminPlaces";
import { canManageAdminPlace, type AdminPlaceDetail, type AdminPlaceFormValues, type AdminPlaceIssue, type AdminPlacePhotoChange } from "../_lib/models";
import { adminPlaceFileKey, reusedAdminPlaceImage } from "../_lib/uploadCache";
import { resolveAdminPlacePhoto, validateAdminPlaceUpdate } from "../_lib/validation";
import { AdminPlaceDeleteDialog } from "./AdminPlaceDeleteDialog";
import { DismissibleDialog } from "./useDismissibleDialog";
import { AdminPlaceDetailView } from "./AdminPlaceDetailView";
import { AdminPlaceForm } from "./AdminPlaceForm";

interface EditSession {
  placeId: string;
  baseline: AdminPlaceFormValues;
  imageUrl: string | null;
  values: AdminPlaceFormValues;
  file: File | null;
  removed: boolean;
}

function sessionFrom(place: AdminPlaceDetail): EditSession {
  return { placeId: place.id, baseline: toAdminPlaceFormValues(place), imageUrl: place.imageUrl, values: toAdminPlaceFormValues(place), file: null, removed: false };
}

export function AdminPlaceDetailScreen({ placeId }: { placeId: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const toast = useToast();
  const client = useQueryClient();
  const listQuery = params.get("list") ?? "";
  const listHref = adminPlaceListHref(params);
  const requestedEdit = params.get("mode") === "edit";
  const [leaving, setLeaving] = useState(false);
  const placeQuery = useAdminPlace(placeId, !leaving);
  const update = useUpdateAdminPlace();
  const remove = useDeleteAdminPlace();
  const place = placeQuery.data;
  const manageable = place ? canManageAdminPlace(place.sourceType, place.visibility) : false;
  const editing = requestedEdit && manageable;
  const [session, setSession] = useState<EditSession | null>(() => (place && editing ? sessionFrom(place) : null));
  const [issues, setIssues] = useState<AdminPlaceIssue[]>([]);
  const [pending, setPending] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [seenPlace, setSeenPlace] = useState(placeId);
  const lock = useRef(false);
  const uploaded = useRef<{ key: string; imageUrl: string } | null>(null);
  if (seenPlace !== placeId) {
    setSeenPlace(placeId);
    setDismissed(false);
    setSession(null);
  } else if (place && editing && !dismissed && session?.placeId !== place.id) {
    setSession(sessionFrom(place));
  }

  const back = () => router.push(listHref);

  const openEdit = () => {
    if (!place || !manageable) return;
    setDismissed(false);
    setSession(sessionFrom(place));
    setIssues([]);
    router.replace(adminPlaceDetailHref(place.id, listQuery, true));
  };

  const closeEdit = () => {
    setDismissed(true);
    setSession(null);
    setIssues([]);
    setConfirmLeave(false);
    if (place) router.replace(adminPlaceDetailHref(place.id, listQuery));
  };

  const dirty = session
    ? hasAdminPlaceChanges(diffAdminPlaceUpdate(session.baseline, session.values, session.removed && session.imageUrl && !session.file ? { action: "delete" } : { action: "keep" })) || session.file != null
    : false;

  const askClose = () => {
    if (!dirty) closeEdit();
    else setConfirmLeave(true);
  };

  const save = async () => {
    if (!place || !session || !manageable || lock.current) return;
    const resolution = resolveAdminPlacePhoto({ existingImageUrl: session.imageUrl, file: session.file, removed: session.removed });
    if (resolution.action === "invalid") {
      setIssues(resolution.issues);
      return;
    }
    lock.current = true;
    setPending(true);
    let stage: "upload" | "save" = "save";
    try {
      let photo: AdminPlacePhotoChange = { action: "keep" };
      if (resolution.action === "delete") photo = { action: "delete" };
      if (resolution.action === "upload" && session.file) {
        stage = "upload";
        const key = adminPlaceFileKey(session.file);
        const imageUrl = reusedAdminPlaceImage(uploaded.current, session.file) ?? (await adminPlaceApi.uploadMockImage(session.file));
        uploaded.current = { key, imageUrl };
        photo = { action: "set", imageUrl };
        stage = "save";
      }
      const patch = diffAdminPlaceUpdate(session.baseline, session.values, photo);
      const checked = validateAdminPlaceUpdate(patch);
      setIssues(checked.issues);
      if (!checked.valid || !hasAdminPlaceChanges(patch)) return;
      const saved = await update.mutateAsync({ id: place.id, ...patch });
      toast({ message: "장소를 수정했어요." });
      setSession(null);
      router.replace(adminPlaceDetailHref(saved.id, listQuery));
    } catch (cause) {
      toast({ message: adminPlaceWriteErrorMessage("update", stage, cause) });
    } finally {
      lock.current = false;
      setPending(false);
    }
  };

  const confirmRemove = async () => {
    if (!place || lock.current) return;
    lock.current = true;
    setPending(true);
    setDeleteError(null);
    flushSync(() => setLeaving(true));
    try {
      await remove.mutateAsync(place.id);
      toast({ message: "장소를 삭제했어요." });
      router.push(listHref);
    } catch (cause) {
      setLeaving(false);
      setDeleteError(deleteFailure(cause));
      if (cause instanceof ApiError && (cause.status === 404 || cause.code === "PLACE_NOT_MANAGEABLE")) {
        void client.invalidateQueries({ queryKey: adminPlaceKeys.detail(place.id) });
        void client.invalidateQueries({ queryKey: adminPlaceKeys.list() });
      }
    } finally {
      lock.current = false;
      setPending(false);
    }
  };

  return (
    <Screen>
      <TopBar title={editing ? "장소 수정" : "장소 상세"} onBack={editing ? askClose : back} />
      {placeQuery.isPending && !place ? (
        <Skeleton className="m-5 h-64" />
      ) : placeQuery.isError && !place ? (
        isMissing(placeQuery.error) ? (
          <div className="px-5 py-12 text-center">
            <p className="text-[15px] font-bold">장소를 찾을 수 없어요.</p>
            <p className="mt-2 text-[13px] text-ink-sub">삭제되었거나 공개되지 않은 장소예요.</p>
            <ButtonLink href={listHref} size="md" className="mt-4">
              목록으로
            </ButtonLink>
          </div>
        ) : (
          <ErrorState message="장소 정보를 불러오지 못했어요." onRetry={() => placeQuery.refetch()} />
        )
      ) : place && editing && session ? (
        <>
          {placeQuery.isError && <p className="mx-5 mt-3 text-[13px] text-ink-sub">최신 정보를 불러오지 못했어요. 입력 중인 내용은 유지됩니다.</p>}
          <AdminPlaceForm
            key={session.placeId}
            mode="edit"
            addressRequired={session.baseline.address.trim().length > 0}
            values={session.values}
            onChange={(patch) => setSession((current) => (current ? { ...current, values: { ...current.values, ...patch } } : current))}
            file={session.file}
            existingImageUrl={session.imageUrl}
            photoRemoved={session.removed}
            onPhotoRemoved={(removed) => {
              setSession((current) => (current ? { ...current, removed, file: removed ? null : current.file } : current));
              if (removed) uploaded.current = null;
            }}
            onFile={(file) => {
              setSession((current) => (current ? { ...current, file } : current));
              if (!file || reusedAdminPlaceImage(uploaded.current, file) == null) uploaded.current = null;
            }}
            issues={issues}
          />
          <BottomCta>
            {pending && (
              <p aria-live="polite" className="pb-2 text-[13px] text-ink-sub">
                {session.file ? "사진과 장소를 저장하고 있어요." : "장소를 저장하고 있어요."}
              </p>
            )}
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="md" className="flex-1" onClick={askClose} disabled={pending}>
                취소
              </Button>
              <Button type="button" size="md" className="flex-1" onClick={save} disabled={pending || !dirty} loading={pending}>
                저장
              </Button>
            </div>
          </BottomCta>
        </>
      ) : place ? (
        <>
          {placeQuery.isError && (
            <div className="mx-5 mt-4 flex items-center justify-between gap-3 rounded-card bg-soft px-4 py-3">
              <p className="text-[13px] text-ink-sub">최신 정보를 불러오지 못했어요.</p>
              <button type="button" onClick={() => placeQuery.refetch()} className="h-9 shrink-0 rounded-control border border-line px-3 text-[13px] font-medium">
                다시 시도
              </button>
            </div>
          )}
          {requestedEdit && !manageable && <p className="px-5 pt-4 text-[13px] text-ink-sub">시스템 제공 장소가 아니므로 조회만 할 수 있어요.</p>}
          <AdminPlaceDetailView place={place} onEdit={openEdit} onDelete={() => { setDeleteError(null); setConfirmDelete(true); }} />
        </>
      ) : null}
      {confirmLeave && (
        <DismissibleDialog titleId="admin-place-cancel-title" onDismiss={() => setConfirmLeave(false)}>
          <div className="w-full max-w-[480px] rounded-card bg-bg p-5">
            <h2 id="admin-place-cancel-title" className="text-[16px] font-bold">
              수정을 취소할까요?
            </h2>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">변경한 내용이 저장되지 않아요.</p>
            <div className="mt-4 flex gap-2">
              <Button type="button" variant="outline" size="md" className="flex-1" onClick={() => setConfirmLeave(false)}>
                계속 수정
              </Button>
              <Button type="button" variant="danger" size="md" className="flex-1" onClick={closeEdit}>
                취소
              </Button>
            </div>
          </div>
        </DismissibleDialog>
      )}
      {confirmDelete && place && (
        <AdminPlaceDeleteDialog name={place.name} address={place.address} pending={pending} error={deleteError} onCancel={() => { if (!pending) setConfirmDelete(false); }} onConfirm={confirmRemove} />
      )}
    </Screen>
  );
}

function isMissing(error: unknown) {
  return error instanceof ApiError && (error.status === 404 || error.code === "PLACE_NOT_FOUND");
}

function deleteFailure(error: unknown) {
  if (error instanceof ApiError && error.status === 0) return "삭제 결과를 확인하지 못했어요. 목록에서 장소가 남아 있는지 확인해 주세요.";
  if (error instanceof ApiError && error.status === 404) return "이미 삭제되었거나 찾을 수 없는 장소예요.";
  if (error instanceof ApiError && error.code === "PLACE_NOT_MANAGEABLE") return "현재 삭제할 수 없는 장소예요.";
  if (error instanceof ApiError && error.status >= 500) return "장소를 삭제하지 못했어요. 다시 시도해 주세요.";
  return errorMessage(error);
}
