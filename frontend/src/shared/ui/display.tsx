import { AlertTriangle, Info } from "lucide-react";
import Image from "next/image";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

/* ── Photo ────────────────────────────────────────── */

interface PhotoProps {
  src: string;
  alt: string;
  className?: string;
  sizes?: string;
  priority?: boolean;
}

/** 영역에 맞춰 크롭하는 사진 (원본 비율을 늘리지 않음). 부모에 크기와 relative가 필요합니다. */
export function Photo({ src, alt, className, sizes = "(max-width: 480px) 100vw, 480px", priority }: PhotoProps) {
  const isLocalBlob = src.startsWith("blob:") || src.startsWith("data:");
  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes={sizes}
      priority={priority}
      unoptimized={isLocalBlob}
      className={cn("object-cover", className)}
      draggable={false}
    />
  );
}

/** 사진 썸네일 (정사각형 등 고정 크기) */
export function Thumb({ src, alt, className }: { src: string | null; alt: string; className?: string }) {
  return (
    <div className={cn("relative shrink-0 overflow-hidden rounded-lg bg-fill", className)}>
      {src && <Photo src={src} alt={alt} sizes="120px" />}
    </div>
  );
}

/* ── Avatar ───────────────────────────────────────── */

export function Avatar({ src, name, size = 36 }: { src?: string | null; name: string; size?: number }) {
  return (
    <span
      className="relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-selected font-bold text-primary-deep"
      style={{ width: size, height: size, fontSize: size * 0.4 }}
    >
      {src ? <Photo src={src} alt="" sizes={`${size * 2}px`} /> : name.slice(0, 1)}
    </span>
  );
}

/* ── Notice (안내 카드) ───────────────────────────── */

const noticeTones = {
  info: { box: "bg-soft text-ink", icon: <Info size={16} className="text-primary-deep" /> },
  warning: { box: "bg-warning-soft text-ink", icon: <AlertTriangle size={16} className="text-warning" /> },
  danger: { box: "bg-danger-soft text-ink", icon: <AlertTriangle size={16} className="text-danger" /> },
};

export function Notice({
  tone = "info",
  title,
  children,
  className,
  icon = true,
}: {
  tone?: keyof typeof noticeTones;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
  icon?: boolean;
}) {
  const t = noticeTones[tone];
  return (
    <div className={cn("flex gap-2.5 rounded-card px-4 py-3.5 text-[13px] leading-relaxed", t.box, className)}>
      {icon && <span className="mt-0.5 shrink-0">{t.icon}</span>}
      <div className="min-w-0 flex-1">
        {title && <div className="font-bold">{title}</div>}
        {children && <div className={cn(title ? "mt-0.5" : undefined, "text-ink-sub")}>{children}</div>}
      </div>
    </div>
  );
}

/* ── Badge ────────────────────────────────────────── */

const badgeTones = {
  lime: "bg-selected text-primary-deep",
  neutral: "bg-fill text-ink-sub",
  warning: "bg-warning-soft text-warning",
  danger: "bg-danger-soft text-danger",
};

export function Badge({ tone = "lime", children, className }: { tone?: keyof typeof badgeTones; children: ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex h-[22px] items-center gap-1 rounded-md px-1.5 text-[11px] font-bold", badgeTones[tone], className)}>
      {children}
    </span>
  );
}

/* ── 로딩 · 빈 상태 ───────────────────────────────── */

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-md bg-fill", className)} />;
}

export function Spinner({ className, label = "불러오는 중" }: { className?: string; label?: string }) {
  return (
    <span
      role="status"
      aria-label={label}
      className={cn("inline-block size-5 animate-spin rounded-full border-2 border-primary border-t-transparent", className)}
    />
  );
}

export function EmptyState({
  icon,
  title,
  description,
  children,
  className,
}: {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center px-6 py-12 text-center", className)}>
      {icon && <div className="mb-4 flex size-14 items-center justify-center rounded-full bg-soft text-primary-deep">{icon}</div>}
      <p className="text-[16px] font-bold text-ink">{title}</p>
      {description && <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">{description}</p>}
      {children && <div className="mt-6 flex w-full flex-col gap-2">{children}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <p className="text-[15px] font-bold text-ink">불러오지 못했어요</p>
      <p className="mt-1.5 text-[13px] text-ink-sub">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-4 h-10 rounded-control border border-line px-4 text-[14px] font-medium">
          다시 시도
        </button>
      )}
    </div>
  );
}

/* ── 섹션 제목 ────────────────────────────────────── */

export function SectionTitle({ children, action, className }: { children: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex items-center justify-between", className)}>
      <h3 className="text-[13px] font-bold text-ink">{children}</h3>
      {action}
    </div>
  );
}
