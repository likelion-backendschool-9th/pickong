import { forwardRef, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";
import { cn } from "@/shared/lib/cn";

const fieldBase =
  "w-full rounded-control border border-transparent bg-soft px-4 text-[15px] text-ink placeholder:text-ink-faint outline-none transition-colors focus:border-primary-deep/50 focus:bg-bg disabled:text-ink-faint";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...rest },
  ref,
) {
  return <input ref={ref} className={cn(fieldBase, "h-12", className)} {...rest} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...rest }, ref) {
    return <textarea ref={ref} className={cn(fieldBase, "min-h-28 resize-none py-3 leading-relaxed", className)} {...rest} />;
  },
);

interface FieldProps {
  label: ReactNode;
  htmlFor?: string;
  hint?: ReactNode;
  error?: ReactNode;
  children: ReactNode;
  className?: string;
  optional?: boolean;
}

export function Field({ label, htmlFor, hint, error, children, className, optional }: FieldProps) {
  return (
    <div className={cn("space-y-2", className)}>
      <label htmlFor={htmlFor} className="block text-[13px] font-bold text-ink">
        {label}
        {optional && <span className="ml-1 font-normal text-ink-faint">(선택)</span>}
      </label>
      {children}
      {error ? (
        <p className="text-[12px] text-danger" role="alert">
          {error}
        </p>
      ) : (
        hint && <p className="text-[12px] text-ink-faint">{hint}</p>
      )}
    </div>
  );
}

/** 값을 보여주고 탭하면 시트를 여는 선택 행 (예: 공개 범위) */
export function SelectTrigger({
  label,
  value,
  onClick,
  id,
}: {
  label?: string;
  value: ReactNode;
  onClick: () => void;
  id?: string;
}) {
  return (
    <button
      id={id}
      type="button"
      onClick={onClick}
      className={cn(fieldBase, "flex h-12 items-center justify-between text-left")}
      aria-label={label}
    >
      <span>{value}</span>
      <span className="text-[12px] text-ink-faint">▾</span>
    </button>
  );
}
