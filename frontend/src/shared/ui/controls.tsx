"use client";

import { Check, Minus, Plus } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

/* ── Chip ─────────────────────────────────────────── */

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean;
  icon?: ReactNode;
  /** solid: 선택 시 먹색 배경 + 흰 글씨, 나머지는 흰 배경 + 옅은 테두리 (내 지도 컬렉션 등) */
  variant?: "default" | "solid";
}

/** 필터·칩. 선택 상태는 라임 크림 배경 + 딥 그린 텍스트 */
export function Chip({ selected, icon, className, children, type = "button", variant = "default", ...rest }: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        "inline-flex h-8 shrink-0 items-center gap-1 rounded-full border px-3 text-[13px] whitespace-nowrap transition-colors",
        variant === "solid"
          ? selected
            ? "border-ink bg-ink font-bold text-white"
            : "border-line bg-bg text-ink active:bg-fill"
          : selected
            ? "border-primary-deep/25 bg-selected font-bold text-primary-deep"
            : "border-line bg-bg text-ink-sub active:bg-fill",
        className,
      )}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}

/* ── CheckMark / RadioMark (표시 전용) ─────────────── */

export function CheckMark({ checked, disabled, round }: { checked: boolean; disabled?: boolean; round?: boolean }) {
  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex size-[22px] shrink-0 items-center justify-center border transition-colors",
        round ? "rounded-full" : "rounded-md",
        checked ? "border-primary-deep/40 bg-selected text-primary-deep" : "border-line bg-bg text-transparent",
        disabled && "opacity-40",
      )}
    >
      <Check size={15} strokeWidth={2.6} />
    </span>
  );
}

export function RadioMark({ checked }: { checked: boolean }) {
  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex size-[22px] shrink-0 items-center justify-center rounded-full border transition-colors",
        checked ? "border-primary-deep bg-selected" : "border-line bg-bg",
      )}
    >
      {checked && <span className="size-2.5 rounded-full bg-primary-deep" />}
    </span>
  );
}

/* ── 선택 행 ──────────────────────────────────────── */

interface SelectRowProps {
  checked: boolean;
  onToggle: () => void;
  title: ReactNode;
  description?: ReactNode;
  leading?: ReactNode;
  trailing?: ReactNode;
  disabled?: boolean;
  type?: "checkbox" | "radio";
  /** 선택 시 연한 라임 배경으로 강조 */
  highlight?: boolean;
  className?: string;
}

export function SelectRow({
  checked,
  onToggle,
  title,
  description,
  leading,
  trailing,
  disabled,
  type = "checkbox",
  highlight,
  className,
}: SelectRowProps) {
  return (
    <div
      role={type}
      aria-checked={checked}
      aria-disabled={disabled}
      tabIndex={disabled ? -1 : 0}
      onClick={() => !disabled && onToggle()}
      onKeyDown={(e) => {
        if (!disabled && (e.key === " " || e.key === "Enter")) {
          e.preventDefault();
          onToggle();
        }
      }}
      className={cn(
        "flex min-h-14 w-full items-center gap-3 rounded-card px-3 py-2.5 text-left transition-colors",
        highlight && checked ? "bg-soft" : "active:bg-fill",
        disabled && "cursor-not-allowed",
        className,
      )}
    >
      {type === "checkbox" ? <CheckMark checked={checked} disabled={disabled} round /> : <RadioMark checked={checked} />}
      {leading}
      <div className="min-w-0 flex-1">
        <div className={cn("text-[14px] font-bold", disabled ? "text-ink-faint" : "text-ink")}>{title}</div>
        {description && <div className="mt-0.5 text-[12px] text-ink-sub">{description}</div>}
      </div>
      {trailing}
    </div>
  );
}

/* ── Tabs (밑줄형) ─────────────────────────────────── */

interface TabsProps<T extends string> {
  items: { value: T; label: ReactNode }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
  size?: "md" | "sm";
}

export function Tabs<T extends string>({ items, value, onChange, className, size = "md" }: TabsProps<T>) {
  return (
    <div role="tablist" className={cn("flex gap-5", className)}>
      {items.map((item) => {
        const active = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(item.value)}
            className={cn(
              "relative h-10 shrink-0",
              size === "md" ? "text-[15px]" : "text-[13px]",
              active ? "font-bold text-ink" : "font-normal text-ink-sub",
            )}
          >
            {item.label}
            {active && <span className="absolute inset-x-0 bottom-0 h-[2px] rounded-full bg-primary-deep" />}
          </button>
        );
      })}
    </div>
  );
}

/* ── Stepper (분 단위) ────────────────────────────── */

interface StepperProps {
  value: number;
  onChange: (value: number) => void;
  step?: number;
  min?: number;
  max?: number;
  format?: (v: number) => string;
  label: string;
}

export function Stepper({ value, onChange, step = 10, min = 10, max = 600, format = (v) => `${v}분`, label }: StepperProps) {
  return (
    <div className="inline-flex items-center gap-1 rounded-full border border-line p-1" role="group" aria-label={label}>
      <button
        type="button"
        aria-label={`${label} 줄이기`}
        disabled={value <= min}
        onClick={() => onChange(Math.max(min, value - step))}
        className="inline-flex size-9 items-center justify-center rounded-full text-ink active:bg-fill disabled:text-ink-faint"
      >
        <Minus size={16} />
      </button>
      <span className="min-w-16 text-center text-[14px] font-bold tabular-nums" aria-live="polite">
        {format(value)}
      </span>
      <button
        type="button"
        aria-label={`${label} 늘리기`}
        disabled={value >= max}
        onClick={() => onChange(Math.min(max, value + step))}
        className="inline-flex size-9 items-center justify-center rounded-full text-ink active:bg-fill disabled:text-ink-faint"
      >
        <Plus size={16} />
      </button>
    </div>
  );
}

/* ── Switch (켬/끔) ───────────────────────────────── */

/** 작은 켬/끔 스위치. 라벨을 누르거나 스위치를 눌러 바꿉니다. */
export function Switch({
  checked,
  onChange,
  label,
  hideLabel = false,
  disabled = false,
  className,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
  /** 라벨을 화면에서 숨기고 화면 낭독기에만 읽힘 (옆에 설명이 따로 있을 때) */
  hideLabel?: boolean;
  /** 누를 수 없음 (예: 바꾸는 요청이 진행 중일 때 연달아 누르지 못하게) */
  disabled?: boolean;
  className?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn("inline-flex h-8 items-center gap-2 text-[13px] text-ink-sub disabled:cursor-not-allowed disabled:opacity-40", className)}
    >
      <span className={hideLabel ? "sr-only" : undefined}>{label}</span>
      <span className={cn("relative h-[18px] w-8 rounded-full transition-colors", checked ? "bg-primary-deep" : "bg-line")} aria-hidden>
        <span
          className={cn(
            "absolute top-[2px] left-[2px] size-[14px] rounded-full bg-white shadow-sm transition-transform",
            checked && "translate-x-[14px]",
          )}
        />
      </span>
    </button>
  );
}
