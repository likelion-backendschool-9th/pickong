"use client";

import { useState, type ReactNode } from "react";
import { errorMessage } from "@/shared/api/errors";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/display";
import { Textarea } from "@/shared/ui/form";
import { normalizeReason, reasonError } from "../_lib/reason";

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
  const validation = reasonError(reason, required ?? true);
  return (
    <BottomSheet
      open
      onClose={onClose}
      dismissible={!loading}
      title={title}
      footer={
        <div className="grid grid-cols-2 gap-2">
          <Button variant="outline" block disabled={loading} onClick={onClose}>
            취소
          </Button>
          <Button
            variant={danger ? "danger" : "primary"}
            block
            disabled={!!validation}
            loading={loading}
            onClick={() => onConfirm(normalizeReason(reason))}
          >
            {confirmLabel}
          </Button>
        </div>
      }
    >
      {description && <div className="mb-3 text-[13px] leading-relaxed text-ink-sub">{description}</div>}
      <fieldset disabled={loading}>{children}</fieldset>
      <Textarea aria-label="사유" disabled={loading} aria-invalid={!!reason && !!validation} value={reason} onChange={(e) => setReason(e.target.value)} placeholder={placeholder} className="min-h-24" />
      <p className="mt-1 text-[12px] text-ink-sub" aria-live="polite">{Array.from(normalizeReason(reason)).length}/200{reason && validation ? ` · ${validation}` : ""}</p>
      {!!error && (
        <Notice tone="danger" className="mt-3">
          {errorMessage(error)}
        </Notice>
      )}
    </BottomSheet>
  );
}
