"use client";

import { useState, type ReactNode } from "react";
import { errorMessage } from "@/shared/api/errors";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/display";
import { Textarea } from "@/shared/ui/form";

/** 반려 · 거절 · 제재처럼 사유를 남겨야 하는 처리 확인 시트 */
export function ReasonSheet({
  open,
  title,
  description,
  placeholder,
  confirmLabel,
  danger,
  required = true,
  loading,
  error,
  children,
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  description?: ReactNode;
  placeholder: string;
  confirmLabel: string;
  danger?: boolean;
  required?: boolean;
  loading: boolean;
  error: unknown;
  children?: ReactNode;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  if (!open) return null;
  return (
    <Form
      title={title}
      description={description}
      placeholder={placeholder}
      confirmLabel={confirmLabel}
      danger={danger}
      required={required}
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
    >
      {children}
    </Form>
  );
}

function Form({
  title,
  description,
  placeholder,
  confirmLabel,
  danger,
  required,
  loading,
  error,
  children,
  onClose,
  onConfirm,
}: Omit<Parameters<typeof ReasonSheet>[0], "open">) {
  const [reason, setReason] = useState("");
  return (
    <BottomSheet
      open
      onClose={onClose}
      title={title}
      footer={
        <div className="grid grid-cols-2 gap-2">
          <Button variant="outline" block onClick={onClose}>
            취소
          </Button>
          <Button
            variant={danger ? "danger" : "primary"}
            block
            disabled={required && !reason.trim()}
            loading={loading}
            onClick={() => onConfirm(reason.trim())}
          >
            {confirmLabel}
          </Button>
        </div>
      }
    >
      {description && <div className="mb-3 text-[13px] leading-relaxed text-ink-sub">{description}</div>}
      {children}
      <Textarea aria-label="사유" value={reason} onChange={(e) => setReason(e.target.value)} placeholder={placeholder} maxLength={200} className="min-h-24" />
      {!!error && (
        <Notice tone="danger" className="mt-3">
          {errorMessage(error)}
        </Notice>
      )}
    </BottomSheet>
  );
}
