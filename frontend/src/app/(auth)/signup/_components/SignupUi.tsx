import { Mail, Smartphone } from "lucide-react";
import Link from "next/link";
import { Fragment, type FormEvent, type ReactNode } from "react";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { TopBar } from "@/shared/ui/layout";
import { AuthHeading } from "../../_components/AuthUi";
import { SIGNUP_STEPS, stepNumber, type SignupMethod, type SignupStep } from "../_lib/signup";

/** 단계 화면이 공통으로 받는 상단 정보: 헤더 제목, 현재 단계, 뒤로 가기 */
export interface StepFrame {
  title: string;
  step: SignupStep;
  /** 없으면 이전 화면(기록이 없으면 로그인)으로 돌아갑니다. */
  onBack?: () => void;
}

/** 진행 표시: o──o──o──o 에서 지나온 단계와 선은 채우고, 현재 단계 점은 크게 보여 줍니다. */
export function StepProgress({ total, current }: { total: number; current: number }) {
  return (
    <div
      role="progressbar"
      aria-label="회원가입 진행 단계"
      aria-valuemin={1}
      aria-valuemax={total}
      aria-valuenow={current}
      aria-valuetext={`전체 ${total}단계 중 ${current}단계`}
      className="flex items-center px-6 py-2 short:py-1"
    >
      {Array.from({ length: total }, (_, i) => {
        const n = i + 1;
        return (
          <Fragment key={n}>
            {n > 1 && (
              <span
                aria-hidden
                className={cn("mx-1 h-0.5 flex-1 rounded-full transition-colors duration-300", n <= current ? "bg-primary" : "bg-line")}
              />
            )}
            <span aria-hidden className="flex size-4 shrink-0 items-center justify-center">
              <span
                className={cn(
                  "rounded-full transition-all duration-300",
                  n === current ? "size-3.5 bg-primary ring-4 ring-primary/20" : "size-2.5",
                  n < current && "bg-primary",
                  n > current && "bg-line",
                )}
              />
            </span>
          </Fragment>
        );
      })}
    </div>
  );
}

/**
 * 회원가입 단계 화면 틀. 모바일 한 화면 안에 들어가도록
 * 헤더 · 진행 표시 · 제목 · 입력칸을 위에, 버튼을 화면 맨 아래에 둡니다.
 */
export function SignupScreen({
  frame,
  heading,
  description,
  onSubmit,
  children,
  footer,
}: {
  frame: StepFrame;
  heading: ReactNode;
  description?: ReactNode;
  onSubmit: (e: FormEvent) => void;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title={frame.title} backHref={routes.login()} onBack={frame.onBack} />
      <StepProgress total={SIGNUP_STEPS.length} current={stepNumber(frame.step)} />
      <form onSubmit={onSubmit} noValidate className="flex flex-1 flex-col px-5 pt-5 pb-[calc(20px+env(safe-area-inset-bottom))] short:pt-3">
        <AuthHeading title={heading} description={description} />
        <div className="mt-7 space-y-5 short:mt-5 short:space-y-4">{children}</div>
        <div className="mt-auto pt-8 short:pt-5">{footer}</div>
      </form>
    </div>
  );
}

const METHOD_LINK: Record<SignupMethod, { href: () => string; label: string; Icon: typeof Mail }> = {
  phone: { href: routes.signup, label: "전화번호로 회원가입", Icon: Smartphone },
  email: { href: routes.signupEmail, label: "이메일로 회원가입", Icon: Mail },
};

/**
 * 다른 가입 방법으로 바꾸는 보조 버튼. 첫 단계 하단에만 둡니다.
 * replace로 이동해 두 가입 화면을 오가도 뒤로 가기는 로그인으로 돌아갑니다.
 */
export function MethodSwitchLink({ to }: { to: SignupMethod }) {
  const { href, label, Icon } = METHOD_LINK[to];
  return (
    <Link
      href={href()}
      replace
      className="mt-3 inline-flex h-12 short:mt-2 w-full items-center justify-center gap-2 rounded-full border border-line bg-bg text-[15px] font-bold text-ink transition-colors active:bg-fill"
    >
      <Icon size={18} strokeWidth={1.8} aria-hidden />
      {label}
    </Link>
  );
}
