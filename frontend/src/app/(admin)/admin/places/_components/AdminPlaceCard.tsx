"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ImageOff } from "lucide-react";
import Image from "next/image";
import { useRef, useState } from "react";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Badge } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";
import { adminPlaceKeys, useDeleteAdminPlace } from "../_api/queries";
import { adminPlaceCategoryText, adminPlaceDetailHref, adminPlaceImageState, adminPlaceSourceText, isKnownAdminPlaceSource } from "../_lib/filterAdminPlaces";
import { canManageAdminPlace, type AdminPlaceSummary } from "../_lib/models";
import { AdminPlaceDeleteDialog } from "./AdminPlaceDeleteDialog";

export function AdminPlacePhoto({ imageUrl, name, className = "size-16" }: { imageUrl: string | null; name: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  const state = adminPlaceImageState(imageUrl, failed);
  return (
    <div className={`relative shrink-0 overflow-hidden rounded-lg bg-fill ${className}`}>
      {state === "show" ? (
        <Image
          src={imageUrl ?? ""}
          alt={`${name} 대표 사진`}
          fill
          sizes="64px"
          unoptimized
          className="object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <span className="flex size-full items-center justify-center text-ink-faint" role="img" aria-label={state === "failed" ? "사진을 불러오지 못했어요" : "사진 없음"}>
          <ImageOff size={18} aria-hidden />
        </span>
      )}
    </div>
  );
}

function AdminPlaceSourceBadge({ sourceType }: { sourceType: string }) {
  const known = isKnownAdminPlaceSource(sourceType);
  return (
    <span className="inline-flex max-w-full flex-wrap items-center gap-1">
      <Badge tone={sourceType === "SYSTEM" ? "lime" : "neutral"}>{adminPlaceSourceText(sourceType)}</Badge>
      {!known && <span className="text-[11px] text-ink-faint">{sourceType}</span>}
    </span>
  );
}

export function AdminPlaceCard({
  place,
  listQuery,
  blocked = false,
  onPending,
}: {
  place: AdminPlaceSummary;
  listQuery: string;
  blocked?: boolean;
  onPending?: (id: string | null) => void;
}) {
  const manageable = canManageAdminPlace(place.sourceType);
  const toast = useToast();
  const client = useQueryClient();
  const remove = useDeleteAdminPlace();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const lock = useRef(false);

  const confirmDelete = async () => {
    if (lock.current || blocked) return;
    lock.current = true;
    setPending(true);
    setDeleteError(null);
    onPending?.(place.id);
    try {
      await remove.mutateAsync(place.id);
      toast({ message: "장소를 삭제했어요." });
      setOpen(false);
    } catch (cause) {
      setDeleteError(deleteMessage(cause));
      if (cause instanceof ApiError && (cause.status === 404 || cause.code === "PLACE_NOT_MANAGEABLE")) {
        void client.invalidateQueries({ queryKey: adminPlaceKeys.list() });
      }
    } finally {
      lock.current = false;
      setPending(false);
      onPending?.(null);
    }
  };

  return (
    <article className="min-w-0 rounded-card border border-line p-4">
      <div className="flex min-w-0 items-start gap-3">
        <AdminPlacePhoto imageUrl={place.imageUrl} name={place.name} />
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-[15px] font-bold">{place.name}</h2>
          <p className="mt-1 text-[13px] break-words text-ink-sub">{place.address?.trim() ? place.address : "주소 미등록"}</p>
          <p className="mt-1 text-[12px] break-words text-ink-faint">{adminPlaceCategoryText(place.category)}</p>
        </div>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1">
        <AdminPlaceSourceBadge sourceType={place.sourceType} />
        <span className="text-[12px] text-ink-sub">{manageable ? "관리 가능" : "읽기 전용"}</span>
      </div>
      <div className="mt-3 flex flex-wrap gap-1.5">
        <ButtonLink href={adminPlaceDetailHref(place.id, listQuery)} size="sm" variant="outline">
          상세
        </ButtonLink>
        {manageable && (
          <>
            <ButtonLink href={adminPlaceDetailHref(place.id, listQuery, true)} size="sm" variant="outline">
              수정
            </ButtonLink>
            <Button size="sm" variant="danger" disabled={blocked || pending} onClick={() => { setDeleteError(null); setOpen(true); }}>
              삭제
            </Button>
          </>
        )}
      </div>
      {open && (
        <AdminPlaceDeleteDialog
          name={place.name}
          address={place.address}
          pending={pending}
          error={deleteError}
          onCancel={() => {
            if (!pending) setOpen(false);
          }}
          onConfirm={confirmDelete}
        />
      )}
    </article>
  );
}

function deleteMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 0) return "삭제 결과를 확인하지 못했어요. 목록에서 장소가 남아 있는지 확인해 주세요.";
  if (error instanceof ApiError && error.status === 404) return "이미 삭제되었거나 찾을 수 없는 장소예요.";
  if (error instanceof ApiError && error.code === "PLACE_NOT_MANAGEABLE") return "현재 삭제할 수 없는 장소예요.";
  if (error instanceof ApiError && error.status >= 500) return "장소를 삭제하지 못했어요. 다시 시도해 주세요.";
  return errorMessage(error);
}
