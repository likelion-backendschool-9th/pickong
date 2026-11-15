import Link from "next/link";
import type { ButtonHTMLAttributes, ComponentProps, ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

type Variant = "primary" | "secondary" | "outline" | "ghost" | "danger";
type Size = "lg" | "md" | "sm";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-on-primary font-bold active:brightness-95 disabled:bg-fill disabled:text-ink-faint",
  secondary: "bg-soft text-primary-deep font-bold active:bg-selected disabled:text-ink-faint",
  outline: "border border-line bg-bg text-ink font-medium active:bg-fill disabled:text-ink-faint",
  ghost: "text-ink-sub font-medium active:bg-fill disabled:text-ink-faint",
  danger: "bg-danger-soft text-danger font-bold active:brightness-95 disabled:opacity-50",
};

const sizes: Record<Size, string> = {
  lg: "h-[52px] px-5 text-[15px]",
  md: "h-11 px-4 text-[14px]",
  sm: "h-9 px-3 text-[13px]",
};

export function buttonClass({
  variant = "primary",
  size = "lg",
  block = false,
  className,
}: { variant?: Variant; size?: Size; block?: boolean; className?: string } = {}) {
  return cn(
    "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-control transition-colors disabled:cursor-not-allowed",
    variants[variant],
    sizes[size],
    block && "w-full",
    className,
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  block?: boolean;
  loading?: boolean;
  icon?: ReactNode;
}

export function Button({
  variant,
  size,
  block,
  loading,
  icon,
  className,
  children,
  disabled,
  type = "button",
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClass({ variant, size, block, className })}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" /> : icon}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends ComponentProps<typeof Link> {
  variant?: Variant;
  size?: Size;
  block?: boolean;
  icon?: ReactNode;
}

export function ButtonLink({ variant, size, block, icon, className, children, ...rest }: ButtonLinkProps) {
  return (
    <Link className={buttonClass({ variant, size, block, className })} {...rest}>
      {icon}
      {children}
    </Link>
  );
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  children: ReactNode;
}

/** 아이콘 전용 버튼. 터치 영역 44px 확보 */
export function IconButton({ label, children, className, type = "button", ...rest }: IconButtonProps) {
  return (
    <button
      type={type}
      aria-label={label}
      title={label}
      className={cn(
        "inline-flex size-11 shrink-0 items-center justify-center rounded-full text-ink active:bg-fill disabled:text-ink-faint",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
