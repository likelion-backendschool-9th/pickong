"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { routes } from "@/shared/config/routes";
import { NO_AGREEMENTS } from "../_lib/signup";
import { PhoneVerifyStep } from "./PhoneVerifyStep";
import { SignupForm, type SignupValue } from "./SignupForm";

/** 단계형 회원가입: 정보 입력 · 약관 동의 → 휴대폰 번호 인증 */
export function SignupFlow() {
  const router = useRouter();
  const [step, setStep] = useState<"form" | "phone">("form");
  const [value, setValue] = useState<SignupValue>({ nickname: "", email: "", password: "", agreements: NO_AGREEMENTS });

  const go = (next: "form" | "phone") => {
    setStep(next);
    window.scrollTo(0, 0);
  };

  if (step === "phone") {
    return (
      <PhoneVerifyStep
        onBack={() => go("form")}
        // 껍데기 단계: 회원가입 API가 생기면 입력값과 인증 결과를 함께 보내고, 성공하면 이동합니다.
        onVerified={() => router.replace(routes.feed())}
      />
    );
  }

  return <SignupForm value={value} onChange={setValue} onNext={() => go("phone")} />;
}
