"use client";

import { Check } from "lucide-react";
import Link from "next/link";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

interface ToastInput {
  message: string;
  detail?: string;
  action?: { label: string; href: string };
}

interface ToastState extends ToastInput {
  id: number;
}

const ToastContext = createContext<((toast: ToastInput) => void) | null>(null);

/** 저장 완료 등 짧은 피드백. 하단 내비게이션 위에 잠시 표시됩니다. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const show = useCallback((input: ToastInput) => {
    clearTimeout(timer.current);
    setToast({ ...input, id: Date.now() });
    timer.current = setTimeout(() => setToast(null), 3200);
  }, []);

  useEffect(() => () => clearTimeout(timer.current), []);

  const value = useMemo(() => show, [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-[calc(88px+env(safe-area-inset-bottom))] z-[60] mx-auto w-full max-w-[480px] px-4"
      >
        {toast && (
          <div
            key={toast.id}
            className="pointer-events-auto flex animate-toast-in items-center gap-2.5 rounded-control bg-ink px-4 py-3 text-white shadow-lg"
          >
            <Check size={18} strokeWidth={2.4} className="shrink-0 text-primary" />
            <div className="min-w-0 flex-1">
              <p className="text-[14px] font-bold">{toast.message}</p>
              {toast.detail && <p className="mt-0.5 text-[12px] text-white/70">{toast.detail}</p>}
            </div>
            {toast.action && (
              <Link href={toast.action.href} className="shrink-0 text-[13px] font-bold text-primary" onClick={() => setToast(null)}>
                {toast.action.label}
              </Link>
            )}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const show = useContext(ToastContext);
  if (!show) throw new Error("useToast는 ToastProvider 안에서 사용해야 합니다.");
  return show;
}
