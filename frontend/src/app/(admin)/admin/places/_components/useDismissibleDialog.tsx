"use client";

import { useEffect, useRef, type ReactNode } from "react";

/** 확인창이 떠 있는 동안 Escape로 닫고, 닫히면 이전 포커스를 되돌립니다. */
export function useDismissibleDialog(onDismiss: () => void, enabled = true) {
  const panelRef = useRef<HTMLDivElement>(null);
  const dismissRef = useRef(onDismiss);
  const enabledRef = useRef(enabled);

  useEffect(() => {
    dismissRef.current = onDismiss;
    enabledRef.current = enabled;
  });

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panelRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (!enabledRef.current) return;
        event.preventDefault();
        dismissRef.current();
        return;
      }
      if (event.key !== "Tab" || !panelRef.current) return;
      const focusable = [...panelRef.current.querySelectorAll<HTMLElement>("button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled])")];
      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || active === panelRef.current || !panelRef.current.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !panelRef.current.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      if (previous?.isConnected) previous.focus();
    };
  }, []);

  return panelRef;
}

export function DismissibleDialog({
  titleId,
  onDismiss,
  enabled = true,
  children,
}: {
  titleId: string;
  onDismiss: () => void;
  enabled?: boolean;
  children: ReactNode;
}) {
  const panelRef = useDismissibleDialog(onDismiss, enabled);
  return (
    <div ref={panelRef} tabIndex={-1} className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 px-5 pb-8 outline-none" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      {children}
    </div>
  );
}
