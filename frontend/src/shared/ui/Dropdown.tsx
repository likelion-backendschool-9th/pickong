"use client";

import { Check } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface DropdownOption<T extends string> {
  value: T;
  label: string;
  icon?: ReactNode;
}

interface DropdownProps<T extends string> {
  value: T;
  options: DropdownOption<T>[];
  onChange: (value: T) => void;
  /** 버튼 안에 보일 내용 (아이콘 등) */
  trigger: ReactNode;
  /** 버튼의 접근성 이름 */
  ariaLabel: string;
  className?: string;
}

/** 작은 선택 메뉴: 버튼을 누르면 오른쪽 정렬 목록이 열리고, 바깥을 누르거나 Esc로 닫힙니다. */
export function Dropdown<T extends string>({ value, options, onChange, trigger, ariaLabel, className }: DropdownProps<T>) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();

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
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex h-10 items-center gap-0.5 rounded-full px-1.5 active:bg-fill"
      >
        {trigger}
      </button>
      {open && (
        <ul
          id={listId}
          role="listbox"
          aria-label={ariaLabel}
          className="absolute top-full right-0 z-20 mt-1 min-w-[148px] overflow-hidden rounded-card bg-bg py-1 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
        >
          {options.map((o) => {
            const selected = o.value === value;
            return (
              <li key={o.value} role="option" aria-selected={selected}>
                <button
                  type="button"
                  onClick={() => {
                    onChange(o.value);
                    setOpen(false);
                  }}
                  className={cn(
                    "flex h-10 w-full items-center gap-2 px-3 text-left text-[14px] active:bg-fill",
                    selected ? "font-bold text-ink" : "text-ink-sub",
                  )}
                >
                  {o.icon && <span className="inline-flex w-4 justify-center">{o.icon}</span>}
                  <span className="flex-1">{o.label}</span>
                  {selected && <Check size={16} className="text-primary-deep" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
