"use client";

import { Button } from "@/shared/ui/Button";
import { useDismissibleDialog } from "./useDismissibleDialog";

export function AdminPlaceDeleteDialog({
  name,
  address,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  name: string;
  address: string | null;
  pending: boolean;
  error?: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const panelRef = useDismissibleDialog(onCancel, !pending);
  return (
    <div ref={panelRef} tabIndex={-1} className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 px-5 pb-8 outline-none" role="dialog" aria-modal="true" aria-labelledby="admin-place-delete-title">
      <div className="w-full max-w-[480px] rounded-card bg-bg p-5">
        <h2 id="admin-place-delete-title" className="text-[16px] font-bold">
          장소를 삭제할까요?
        </h2>
        <p className="mt-2 text-[15px] font-bold">{name}</p>
        <p className="mt-1 text-[13px] text-ink-sub">{address?.trim() ? address : "주소 미등록"}</p>
        <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">선택한 장소는 지도 관리 목록에서 더 이상 표시되지 않아요.</p>
        {pending && (
          <p aria-live="polite" className="mt-3 text-[13px] text-ink-sub">
            삭제하고 있어요.
          </p>
        )}
        {error && (
          <p className="mt-3 text-[13px] text-danger" role="alert">
            {error}
          </p>
        )}
        <div className="mt-4 flex gap-2">
          <Button type="button" variant="outline" size="md" className="flex-1" onClick={onCancel} disabled={pending}>
            취소
          </Button>
          <Button type="button" variant="danger" size="md" className="flex-1" onClick={onConfirm} disabled={pending} loading={pending}>
            삭제
          </Button>
        </div>
      </div>
    </div>
  );
}
