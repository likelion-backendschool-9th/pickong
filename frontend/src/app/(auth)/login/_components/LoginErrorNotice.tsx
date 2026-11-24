"use client";

import { useSearchParams } from "next/navigation";
import { loginErrorMessage } from "../_lib/loginError";

/** 소셜 로그인 실패 · 가입 시간 만료 · 세션 만료로 돌아왔을 때 버튼 위에 사유를 보여 줍니다. */
export function LoginErrorNotice() {
  const message = loginErrorMessage(useSearchParams().get("error"));
  if (!message) return null;
  return (
    <p className="mb-4 rounded-[14px] bg-danger-soft px-4 py-3 text-[14px] leading-relaxed text-ink" role="alert">
      {message}
    </p>
  );
}
