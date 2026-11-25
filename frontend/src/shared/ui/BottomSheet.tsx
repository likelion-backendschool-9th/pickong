"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef, type PointerEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { cn } from "@/shared/lib/cn";

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  /** 시트 하단 고정 영역 (CTA) */
  footer?: ReactNode;
  showClose?: boolean;
  className?: string;
}

/**
 * 바텀시트. 닫아도 뒤 화면 상태(사진 번호·스크롤 위치)는 그대로 유지됩니다.
 * 뒤 화면을 언마운트하지 않고 위에 겹쳐 그리기 때문입니다.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  showClose = true,
  className,
}: BottomSheetProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  /** 드래그 시작 시점의 Y 좌표. null이면 드래그 중 아님 */
  const dragStartY = useRef<number | null>(null);

  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();

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

  /** 핸들에 손가락(포인터)이 닿으면 드래그 시작. setPointerCapture로 핸들 밖으로 나가도 추적 */
  const handleDragStart = (e: PointerEvent<HTMLDivElement>) => {
    dragStartY.current = e.clientY;
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  /** 드래그 중: 아래 방향만 허용하고 시트를 손가락 위치만큼 translateY */
  const handleDragMove = (e: PointerEvent<HTMLDivElement>) => {
    if (dragStartY.current === null || !panelRef.current) return;
    const dy = Math.max(0, e.clientY - dragStartY.current);
    panelRef.current.style.transition = "none";
    panelRef.current.style.transform = `translateY(${dy}px)`;
  };

  /** 손가락을 뗐을 때: 임계값(시트 높이 30%) 초과면 닫고, 아니면 제자리로 복귀 */
  const handleDragEnd = (e: PointerEvent<HTMLDivElement>) => {
    if (dragStartY.current === null || !panelRef.current) return;
    const dy = Math.max(0, e.clientY - dragStartY.current);
    dragStartY.current = null;

    const threshold = panelRef.current.offsetHeight * 0.3;
    if (dy > threshold) {
      // 임계값 초과 → 아래로 슬라이드 아웃 후 onClose 호출
      panelRef.current.style.transition = "transform 220ms ease-in";
      panelRef.current.style.transform = "translateY(100%)";
      panelRef.current.addEventListener("transitionend", () => onCloseRef.current(), { once: true });
    } else {
      // 임계값 미만 → 제자리로 복귀
      panelRef.current.style.transition = "transform 300ms cubic-bezier(0.2, 0.8, 0.2, 1)";
      panelRef.current.style.transform = "translateY(0)";
    }
  };

  if (!open || typeof document === "undefined") return null;

  return createPortal(
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 animate-fade-in bg-black/40" onClick={onClose} aria-hidden />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
        className={cn(
          "absolute inset-x-0 bottom-0 mx-auto flex max-h-[88dvh] w-full max-w-[480px] animate-sheet-up flex-col rounded-t-[20px] bg-bg outline-none",
          className,
        )}
      >
        {/* touch-none: 브라우저 스크롤이 대신 작동하는 걸 막음 */}
        <div
          className="flex touch-none justify-center pt-2.5 pb-1 cursor-grab active:cursor-grabbing"
          aria-hidden
          onPointerDown={handleDragStart}
          onPointerMove={handleDragMove}
          onPointerUp={handleDragEnd}
          onPointerCancel={handleDragEnd}
        >
          <span className="h-1 w-10 rounded-full bg-line" />
        </div>
        {(title || showClose) && (
          <div className="flex items-start gap-2 px-5 pt-2 pb-3">
            <div className="min-w-0 flex-1">
              {title && (
                <h2 id={titleId} className="text-[18px] font-bold leading-snug text-ink">
                  {title}
                </h2>
              )}
              {description && <p className="mt-1 text-[13px] text-ink-sub">{description}</p>}
            </div>
            {showClose && (
              <button
                type="button"
                onClick={onClose}
                aria-label="닫기"
                className="-mt-1 -mr-2 inline-flex size-10 items-center justify-center rounded-full text-ink active:bg-fill"
              >
                <X size={22} strokeWidth={1.6} />
              </button>
            )}
          </div>
        )}
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 pb-4">{children}</div>
        {footer && (
          <div className="border-t border-line px-5 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))]">{footer}</div>
        )}
      </div>
    </div>,
    document.body,
  );
}
