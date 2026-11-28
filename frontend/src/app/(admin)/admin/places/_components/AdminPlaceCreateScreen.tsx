"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { adminPlaceWriteErrorMessage } from "../_lib/saveError";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { adminPlaceApi } from "../_api/adminPlaceApi";
import { useCreateAdminPlace } from "../_api/queries";
import type { AdminPlaceFormValues, AdminPlaceIssue } from "../_lib/models";
import { adminPlaceFileKey, reusedAdminPlaceImage } from "../_lib/uploadCache";
import { validateAdminPlaceForm, validateAdminPlacePhoto } from "../_lib/validation";
import { AdminPlaceForm } from "./AdminPlaceForm";
import { DismissibleDialog } from "./useDismissibleDialog";

const EMPTY: AdminPlaceFormValues = { name: "", address: "", category: "", location: null, businessHours: [], hoursExceptions: [] };

const FIELD_ID: Record<string, string> = {
  name: "admin-place-name",
  address: "admin-place-address",
  category: "admin-place-category",
  latitude: "admin-place-location-error",
  longitude: "admin-place-location-error",
  photo: "admin-place-photo",
};

function focusField(field: string) {
  const id = FIELD_ID[field] ?? (field.startsWith("businessHours") ? "admin-place-hours" : field.startsWith("hoursExceptions") ? "admin-place-exceptions" : "");
  const node = id ? document.getElementById(id) : null;
  node?.focus();
  node?.scrollIntoView({ block: "center" });
}

function dirty(values: AdminPlaceFormValues, file: File | null) {
  return Boolean(values.name || values.address || values.category || values.location || values.businessHours.length || values.hoursExceptions.length || file);
}

export function AdminPlaceCreateScreen() {
  const router = useRouter();
  const toast = useToast();
  const create = useCreateAdminPlace();
  const [values, setValues] = useState(EMPTY);
  const [file, setFile] = useState<File | null>(null);
  const [issues, setIssues] = useState<AdminPlaceIssue[]>([]);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [pending, setPending] = useState(false);
  const uploaded = useRef<{ key: string; imageUrl: string } | null>(null);
  const saved = useRef(false);
  const lock = useRef(false);

  const leave = () => router.push(routes.adminPlaces());

  const askLeave = () => {
    if (saved.current || !dirty(values, file)) leave();
    else setConfirmLeave(true);
  };

  const submit = async () => {
    if (lock.current) return;
    const checked = validateAdminPlaceForm(values);
    const photo = file ? validateAdminPlacePhoto(file) : { valid: true, issues: [] };
    const nextIssues = [...checked.issues, ...photo.issues];
    setIssues(nextIssues);
    if (nextIssues.length > 0) {
      focusField(nextIssues[0].field);
      return;
    }
    lock.current = true;
    setPending(true);
    let stage: "upload" | "save" = "save";
    try {
      let imageUrl: string | undefined;
      if (file) {
        stage = "upload";
        const key = adminPlaceFileKey(file);
        imageUrl = reusedAdminPlaceImage(uploaded.current, file) ?? (await adminPlaceApi.uploadMockImage(file));
        uploaded.current = { key, imageUrl };
        stage = "save";
      }
      await create.mutateAsync(imageUrl ? { ...values, photo: { action: "set", imageUrl } } : values);
      saved.current = true;
      toast({ message: "장소를 등록했어요." });
      leave();
    } catch (error) {
      toast({ message: adminPlaceWriteErrorMessage("create", stage, error) });
    } finally {
      lock.current = false;
      setPending(false);
    }
  };

  return (
    <Screen>
      <TopBar title="장소 추가" onBack={askLeave} />
      <AdminPlaceForm
        values={values}
        onChange={(patch) => setValues((current) => ({ ...current, ...patch }))}
        file={file}
        issues={issues}
        onFile={(next) => {
          setFile(next);
          if (!next || reusedAdminPlaceImage(uploaded.current, next) == null) uploaded.current = null;
        }}
      />
      <BottomCta>
        {pending && (
          <p aria-live="polite" className="pb-2 text-[13px] text-ink-sub">
            {file ? "사진과 장소를 등록하고 있어요." : "장소를 등록하고 있어요."}
          </p>
        )}
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="md" className="flex-1" onClick={askLeave} disabled={pending}>
            취소
          </Button>
          <Button type="button" size="md" className="flex-1" onClick={submit} disabled={pending} loading={pending}>
            등록
          </Button>
        </div>
      </BottomCta>
      {confirmLeave && (
        <DismissibleDialog titleId="admin-place-leave-title" onDismiss={() => setConfirmLeave(false)}>
          <div className="w-full max-w-[480px] rounded-card bg-bg p-5">
            <h2 id="admin-place-leave-title" className="text-[16px] font-bold">
              작성을 취소할까요?
            </h2>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">입력한 내용이 사라져요. 등록한 장소는 없습니다.</p>
            <div className="mt-4 flex gap-2">
              <Button type="button" variant="outline" size="md" className="flex-1" onClick={() => setConfirmLeave(false)}>
                계속 작성
              </Button>
              <Button type="button" variant="danger" size="md" className="flex-1" onClick={leave}>
                나가기
              </Button>
            </div>
          </div>
        </DismissibleDialog>
      )}
    </Screen>
  );
}
