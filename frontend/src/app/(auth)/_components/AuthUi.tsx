import { Check } from "lucide-react";
import type { InputHTMLAttributes, ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

/*
 * 시작 · 로그인 · 온보딩(회원가입) 화면 전용 UI.
 * 앱 안쪽 폼(연한 라임 채움 입력창)과 달리 흰 바탕 + 회색 테두리 입력창, 알약 모양 CTA를 씁니다.
 */

/** 알약 모양 주요 버튼 (시작하기 · 다음 · 동의하고 시작하기) */
export const pillButtonClass =
  "inline-flex h-14 w-full items-center justify-center rounded-full bg-primary text-[17px] font-bold text-on-primary transition active:brightness-95 disabled:cursor-not-allowed disabled:bg-fill disabled:text-ink-faint";

export function AuthHeading({ title, description, className }: { title: ReactNode; description?: ReactNode; className?: string }) {
  return (
    <div className={className}>
      <h2 className="text-[28px] leading-[1.3] font-bold tracking-[-0.6px] text-ink">{title}</h2>
      {description && <p className="mt-2.5 text-[15px] leading-relaxed text-ink-sub">{description}</p>}
    </div>
  );
}

interface AuthInputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export function AuthInput({ invalid, className, ...rest }: AuthInputProps) {
  return (
    <input
      aria-invalid={invalid || undefined}
      className={cn(
        "h-14 w-full min-w-0 rounded-[14px] border bg-bg px-5 text-[16px] text-ink outline-none transition-colors placeholder:text-ink-faint",
        invalid ? "border-danger" : "border-line focus:border-primary-deep/60",
        className,
      )}
      {...rest}
    />
  );
}

/** 라벨 + 입력창 + 안내/오류 문구 */
export function AuthField({
  label,
  htmlFor,
  hint,
  error,
  children,
}: {
  label: ReactNode;
  htmlFor: string;
  hint?: ReactNode;
  error?: string | null;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-2.5 block text-[15px] font-bold text-ink">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} className="mt-2 text-[13px] text-danger" role="alert">
          {error}
        </p>
      ) : (
        hint && <p className="mt-2 text-[13px] text-ink-sub">{hint}</p>
      )}
    </div>
  );
}

/** 라임 크림 바탕의 체크박스. 실제 입력은 숨긴 native checkbox가 담당합니다. */
export function AuthCheckbox({
  checked,
  onChange,
  children,
  className,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={cn("flex min-w-0 cursor-pointer items-center gap-2.5", className)}>
      <input type="checkbox" className="peer sr-only" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span
        aria-hidden
        className="inline-flex size-7 shrink-0 items-center justify-center rounded-md border border-primary-deep/10 bg-selected text-ink peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-deep"
      >
        {checked && <Check size={18} strokeWidth={2.4} />}
      </span>
      <span className="min-w-0">{children}</span>
    </label>
  );
}
