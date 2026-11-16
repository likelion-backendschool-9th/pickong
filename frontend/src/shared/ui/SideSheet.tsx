"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { cn } from "@/shared/lib/cn";

const DURATION = 260;

interface SideSheetProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  children: ReactNode;
  className?: string;
}

/**
 * 오른쪽에서 밀려 들어오는 패널. 페이지를 이동하지 않고 현재 화면 위에 겹쳐 그립니다.
 * 닫을 때도 오른쪽으로 밀려 나간 뒤 사라집니다. 바깥(어두운 영역)을 누르거나 Esc로 닫힙니다.
 */
export function SideSheet({ open, onClose, title, children, className }: SideSheetProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  /** mounted: DOM에 있음, shown: 화면 안으로 들어온 상태 (전환 효과용) */
  const [mounted, setMounted] = useState(open);
  const [shown, setShown] = useState(false);

  useEffect(() => {
    onCloseRef.current = onClose;
  });

  // 열리면 바로 DOM에 올리고, 닫히면 바로 밀어내기 시작 (렌더 중 상태 보정)
  if (open && !mounted) setMounted(true);
  if (!open && shown) setShown(false);

  // 열기: 화면 밖에 그린 다음 프레임에 안으로 밀어 넣기 / 닫기: 밀려 나간 뒤 DOM에서 제거
  useEffect(() => {
    if (open) {
      const raf = requestAnimationFrame(() => requestAnimationFrame(() => setShown(true)));
      return () => cancelAnimationFrame(raf);
    }
    const t = setTimeout(() => setMounted(false), DURATION);
    return () => clearTimeout(t);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus({ preventScroll: true });

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.({ preventScroll: true });
    };
  }, [open]);

  if (!mounted || typeof document === "undefined") return null;

  return createPortal(
    <div className="fixed inset-0 z-50 mx-auto w-full max-w-[480px] overflow-hidden">
      <div
        className={cn("absolute inset-0 bg-black/40 transition-opacity ease-out motion-reduce:transition-none", shown ? "opacity-100" : "opacity-0")}
        style={{ transitionDuration: `${DURATION}ms` }}
        onClick={onClose}
        aria-hidden
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
        className={cn(
          "absolute inset-y-0 right-0 flex w-[84%] max-w-[400px] flex-col bg-bg pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] shadow-[-4px_0_24px_rgba(0,0,0,0.12)] outline-none",
          "transition-transform ease-[cubic-bezier(0.2,0.8,0.2,1)] motion-reduce:transition-none",
          shown ? "translate-x-0" : "translate-x-full",
          className,
        )}
        style={{ transitionDuration: `${DURATION}ms` }}
      >
        <div className="flex h-[52px] shrink-0 items-center gap-2 pr-2 pl-5">
          {title && (
            <h2 id={titleId} className="min-w-0 flex-1 truncate text-[17px] font-bold text-ink">
              {title}
            </h2>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="ml-auto inline-flex size-11 items-center justify-center rounded-full text-ink active:bg-fill"
          >
            <X size={22} strokeWidth={1.6} />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
