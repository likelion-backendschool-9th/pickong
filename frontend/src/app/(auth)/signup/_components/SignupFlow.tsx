"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { normalizeName, suggestedName } from "@/entities/user";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { ErrorState, Spinner } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useCancelSignup, useCompleteSignup, useSignupSession, type SignupSession } from "../_api/signupApi";
import {
  NO_AGREEMENTS,
  agreedTermsTypes,
  previousStep,
  stepForSignupError,
  suggestedNickname,
  type Agreements,
  type SignupStep,
} from "../_lib/signup";
import { NicknameStep } from "./NicknameStep";
import { ProfileStep } from "./ProfileStep";
import { TermsSheet } from "./TermsSheet";
import type { StepFrame } from "./SignupUi";

const TITLE = "회원가입";

/** 가입 세션 응답에 따라 보낼 곳. 이동할 필요가 없으면 null */
function redirectFor(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.code === "ALREADY_REGISTERED") return routes.feed();
  if (error.status === 401) return routes.login("SIGNUP_SESSION_EXPIRED");
  return null;
}

/**
 * 최초 가입 온보딩. 소셜 인증을 마친 신규 회원만 서버가 이 화면으로 보냅니다.
 * 가입 상태의 원본은 서버의 임시 가입 세션이라, 새로고침 · 뒤로 가기 · 재접속에도 다음처럼 일관되게 동작합니다.
 * - 세션이 살아 있으면 소셜 계정 정보로 첫 단계부터 다시 채웁니다.
 * - 세션이 없거나 만료되면 로그인 화면으로, 이미 가입을 마쳤으면 피드로 보냅니다.
 */
export function SignupFlow() {
  const router = useRouter();
  const session = useSignupSession();
  const redirectTo = redirectFor(session.error);

  useEffect(() => {
    if (redirectTo) router.replace(redirectTo);
  }, [redirectTo, router]);

  if (session.isPending || redirectTo) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner />
      </div>
    );
  }
  if (session.isError) {
    return (
      <div className="flex min-h-dvh flex-col">
        <TopBar title={TITLE} backHref={routes.login()} />
        <ErrorState message={errorMessage(session.error)} onRetry={() => session.refetch()} />
      </div>
    );
  }
  return <OnboardingSteps session={session.data} />;
}

interface OnboardingValue {
  /** 아이디 (영문, 중복 불가) */
  nickname: string;
  /** 이름 (중복 가능, 비우면 null로 보냄) */
  name: string;
  useSocialImage: boolean;
  agreements: Agreements;
}

/** 아이디 → 프로필 사진 · 이름(선택, 건너뛰기 가능) → 약관 동의 시트 */
function OnboardingSteps({ session }: { session: SignupSession }) {
  const router = useRouter();
  const toast = useToast();
  const complete = useCompleteSignup();
  const cancel = useCancelSignup();
  const [step, setStep] = useState<SignupStep>("nickname");
  const [leaving, setLeaving] = useState(false);
  const [value, setValue] = useState<OnboardingValue>(() => ({
    nickname: suggestedNickname(session.email),
    name: suggestedName(session.name),
    useSocialImage: session.profileImageUrl !== null,
    agreements: NO_AGREEMENTS,
  }));
  const patch = (next: Partial<OnboardingValue>) => setValue((v) => ({ ...v, ...next }));

  const go = (next: SignupStep) => {
    complete.reset();
    setStep(next);
    window.scrollTo(0, 0);
  };
  const back = () => {
    const prev = previousStep(step);
    if (prev) go(prev);
    else setLeaving(true);
  };
  const frame: StepFrame = { title: TITLE, step, onBack: back };

  const finish = () => {
    if (complete.isPending) return;
    const nickname = value.nickname;
    const name = normalizeName(value.name);
    complete.mutate(
      {
        username: nickname,
        name,
        useSocialProfileImage: value.useSocialImage && session.profileImageUrl !== null,
        agreedTerms: agreedTermsTypes(value.agreements),
      },
      {
        onSuccess: () => {
          toast({ message: `${nickname}님, 찜콩에 오신 걸 환영해요` });
          // replace: 가입을 마친 뒤 뒤로 가기로 온보딩에 돌아오지 않게 합니다.
          router.replace(routes.feed());
        },
        onError: (error) => {
          const redirectTo = redirectFor(error);
          if (redirectTo) return router.replace(redirectTo);
          const target = error instanceof ApiError ? stepForSignupError(error.code) : null;
          // 고칠 단계가 따로 있으면 그 단계로 돌려보내고, 약관 문제는 시트 안에 그대로 보여 줍니다.
          if (target && target !== "terms") {
            toast({ message: errorMessage(error) });
            setStep(target);
          }
        },
      },
    );
  };

  const leave = () => cancel.mutate(undefined, { onSettled: () => router.replace(routes.login()) });

  return (
    <>
      {step === "nickname" ? (
        <NicknameStep frame={frame} nickname={value.nickname} onChange={(nickname) => patch({ nickname })} onNext={() => go("profile")} />
      ) : (
        <ProfileStep
          frame={frame}
          provider={session.provider}
          nickname={value.nickname}
          socialImageUrl={session.profileImageUrl}
          useSocialImage={value.useSocialImage}
          name={value.name}
          onChange={patch}
          onNext={() => go("terms")}
          // 건너뛰기: 사진 · 이름 없이 시작합니다. (기본 이미지, 이름 없음)
          onSkip={() => {
            patch({ name: "", useSocialImage: false });
            go("terms");
          }}
        />
      )}

      <TermsSheet
        open={step === "terms"}
        agreements={value.agreements}
        onChange={(agreements) => patch({ agreements })}
        onClose={() => {
          if (!complete.isPending) go("profile");
        }}
        onConfirm={finish}
        pending={complete.isPending}
        error={step === "terms" && complete.isError ? errorMessage(complete.error) : null}
      />

      <BottomSheet
        open={leaving}
        onClose={() => setLeaving(false)}
        title="가입을 그만둘까요?"
        description="입력한 내용은 저장되지 않아요. 다음에 같은 계정으로 로그인하면 처음부터 다시 진행해요."
      >
        <div className="grid grid-cols-2 gap-2 pt-2 pb-[calc(12px+env(safe-area-inset-bottom))]">
          <Button variant="outline" block onClick={() => setLeaving(false)}>
            계속 가입하기
          </Button>
          <Button variant="danger" block loading={cancel.isPending} onClick={leave}>
            그만두기
          </Button>
        </div>
      </BottomSheet>
    </>
  );
}
