"use client";

import { MoreHorizontal } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface ActionMenuItem {
  label: string;
  icon?: ReactNode;
  onSelect: () => void;
  danger?: boolean;
}

/** ⋯ 버튼을 누르면 열리는 동작 메뉴 (수정 · 삭제 등). 바깥을 누르거나 Esc로 닫힙니다. */
export function ActionMenu({ items, label = "더 보기", className }: { items: ActionMenuItem[]; label?: string; className?: string }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className={cn("relative", className)}>
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex size-9 items-center justify-center rounded-full text-ink-sub active:bg-fill"
      >
        <MoreHorizontal size={20} />
      </button>
      {open && (
        <ul
          id={menuId}
          role="menu"
          className="absolute top-full right-0 z-20 mt-1 min-w-[132px] overflow-hidden rounded-card bg-bg py-1 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
        >
          {items.map((it) => (
            <li key={it.label} role="none">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  it.onSelect();
                }}
                className={cn("flex h-10 w-full items-center gap-2 px-3 text-left text-[14px] active:bg-fill", it.danger ? "text-danger" : "text-ink")}
              >
                {it.icon && <span className="inline-flex w-4 justify-center">{it.icon}</span>}
                {it.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
