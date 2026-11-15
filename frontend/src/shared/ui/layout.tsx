"use client";

import { ChevronLeft } from "lucide-react";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

interface TopBarProps {
  title?: ReactNode;
  /** 뒤로가기 기록이 없을 때 이동할 경로 */
  backHref?: string;
  onBack?: () => void;
  hideBack?: boolean;
  right?: ReactNode;
  className?: string;
}

/** 세부 작업 화면의 상단 헤더: 뒤로가기 + 화면 제목 */
export function TopBar({ title, backHref = "/feed", onBack, hideBack, right, className }: TopBarProps) {
  const router = useRouter();
  const goBack = () => {
    if (onBack) return onBack();
    if (window.history.length > 1) router.back();
    else router.push(backHref);
  };

  return (
    <header className={cn("sticky top-0 z-30 flex h-[52px] items-center gap-1 bg-bg px-2", className)}>
      {!hideBack && (
        <button
          type="button"
          onClick={goBack}
          aria-label="뒤로 가기"
          className="inline-flex size-11 items-center justify-center rounded-full text-ink active:bg-fill"
        >
          <ChevronLeft size={26} strokeWidth={1.6} />
        </button>
      )}
      <h1 className={cn("min-w-0 flex-1 truncate text-[17px] font-bold text-ink", hideBack && "pl-3")}>{title}</h1>
      {right && <div className="flex shrink-0 items-center">{right}</div>}
    </header>
  );
}

/** 화면 하단 CTA 영역. 본문 흐름 끝에 붙어 스크롤 시 하단에 고정됩니다. */
export function BottomCta({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "sticky bottom-0 z-20 mt-auto bg-bg/95 px-5 pt-3 pb-[calc(16px+env(safe-area-inset-bottom))] backdrop-blur",
        className,
      )}
    >
      {children}
    </div>
  );
}

/** 세부 작업 화면 컨테이너: 화면 높이를 채우고 하단 CTA를 맨 아래에 둡니다. */
export function Screen({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("flex min-h-dvh flex-col", className)}>{children}</div>;
}
