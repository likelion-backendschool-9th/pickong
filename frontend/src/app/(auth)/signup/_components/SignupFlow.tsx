"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { routes } from "@/shared/config/routes";
import { NO_AGREEMENTS, previousStep, type Agreements, type SignupMethod, type SignupStep } from "../_lib/signup";
import { NicknameStep } from "./NicknameStep";
import { PasswordStep } from "./PasswordStep";
import { TermsSheet } from "./TermsSheet";
import { VerifyStep } from "./VerifyStep";
import type { StepFrame } from "./SignupUi";

interface SignupValue {
  /** 1단계에서 입력한 휴대폰 번호(전화번호 가입) 또는 이메일(이메일 가입) */
  contact: string;
  /** 인증을 마친 contact. contact를 바꾸면 다시 인증해야 합니다. */
  verifiedContact: string | null;
  password: string;
  passwordConfirm: string;
  nickname: string;
  agreements: Agreements;
}

const EMPTY: SignupValue = {
  contact: "",
  verifiedContact: null,
  password: "",
  passwordConfirm: "",
  nickname: "",
  agreements: NO_AGREEMENTS,
};

const TITLES: Record<SignupMethod, string> = { phone: "전화번호로 회원가입", email: "이메일로 회원가입" };

/**
 * 단계형 회원가입. 한 화면에 한 가지만 묻습니다.
 * 인증(휴대폰 번호 · 이메일) → 비밀번호 → 닉네임(실시간 중복 확인) → 약관 동의 시트
 * 전화번호 가입은 이메일을 받지 않습니다.
 */
export function SignupFlow({ method }: { method: SignupMethod }) {
  const router = useRouter();
  const [step, setStep] = useState<SignupStep>("verify");
  const [value, setValue] = useState<SignupValue>(EMPTY);
  const patch = (next: Partial<SignupValue>) => setValue((v) => ({ ...v, ...next }));

  const go = (next: SignupStep) => {
    setStep(next);
    window.scrollTo(0, 0);
  };
  const prev = previousStep(step);
  const frame: StepFrame = { title: TITLES[method], step, onBack: prev ? () => go(prev) : undefined };

  if (step === "verify") {
    return (
      <VerifyStep
        frame={frame}
        method={method}
        contact={value.contact}
        verified={value.verifiedContact !== null && value.verifiedContact === value.contact}
        onContactChange={(contact) => patch({ contact })}
        onVerified={() => {
          patch({ verifiedContact: value.contact });
          go("password");
        }}
      />
    );
  }

  if (step === "password") {
    return (
      <PasswordStep
        frame={frame}
        verifiedEmail={method === "email" ? value.verifiedContact : null}
        password={value.password}
        confirm={value.passwordConfirm}
        onChange={patch}
        onNext={() => go("nickname")}
      />
    );
  }

  return (
    <>
      <NicknameStep frame={frame} nickname={value.nickname} onChange={(nickname) => patch({ nickname })} onNext={() => setStep("terms")} />
      <TermsSheet
        open={step === "terms"}
        agreements={value.agreements}
        onChange={(agreements) => patch({ agreements })}
        onClose={() => setStep("nickname")}
        // 껍데기 단계: 회원가입 API가 생기면 입력값과 인증 결과를 함께 보내고, 성공하면 이동합니다.
        onConfirm={() => router.replace(routes.feed())}
      />
    </>
  );
}
