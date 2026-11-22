"use client";

import { CircleCheck, CircleX, LoaderCircle } from "lucide-react";
import type { FormEvent } from "react";
import { useDebounced } from "@/shared/lib/useDebounced";
import { AuthField, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { useNicknameAvailability } from "../_api/signupApi";
import { NICKNAME_MAX, NICKNAME_MIN, nicknameError, nicknameStatus, type NicknameStatus } from "../_lib/signup";
import { SignupScreen, type StepFrame } from "./SignupUi";

/** 입력칸 오른쪽 표시: 확인 중(회색 원) · 사용 가능(초록 체크) · 사용 불가(빨간 X) */
function StatusIcon({ status }: { status: NicknameStatus }) {
  const base = "pointer-events-none absolute top-1/2 right-4 -translate-y-1/2";
  if (status === "checking") return <LoaderCircle size={22} className={`${base} animate-spin text-ink-faint`} aria-hidden />;
  if (status === "available") return <CircleCheck size={22} className={`${base} text-primary-deep`} aria-hidden />;
  if (status === "invalid" || status === "taken") return <CircleX size={22} className={`${base} text-danger`} aria-hidden />;
  return null;
}

/**
 * 3단계: 닉네임. 입력이 멈추면 바로 형식과 중복 여부를 확인해 입력칸 옆에 표시합니다.
 * 사용할 수 있는 닉네임일 때만 다음(약관 동의 시트)으로 넘어갑니다.
 */
export function NicknameStep({
  frame,
  nickname,
  onChange,
  onNext,
}: {
  frame: StepFrame;
  nickname: string;
  onChange: (nickname: string) => void;
  onNext: () => void;
}) {
  const settled = useDebounced(nickname, 300);
  const checkable = settled.trim() !== "" && !nicknameError(settled);
  const availability = useNicknameAvailability(settled.trim(), checkable);
  const status = nicknameStatus(nickname, settled, availability.isError ? null : availability.data?.available);

  const error =
    status === "invalid"
      ? nicknameError(nickname)
      : status === "taken"
        ? "이미 사용 중인 닉네임이에요."
        : status === "error"
          ? "중복 확인을 하지 못했어요. 잠시 후 다시 시도해 주세요."
          : null;
  const hint =
    status === "available" ? (
      <span className="text-primary-deep">사용할 수 있는 닉네임이에요.</span>
    ) : status === "checking" ? (
      "확인하고 있어요…"
    ) : (
      `${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`
    );

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (status === "available") onNext();
  };

  return (
    <SignupScreen
      frame={frame}
      heading={<>찜콩에서 쓸<br />닉네임을 정해 주세요</>}
      description="닉네임으로도 로그인할 수 있어요."
      onSubmit={submit}
      footer={
        <button type="submit" disabled={status !== "available"} className={pillButtonClass}>
          다음
        </button>
      }
    >
      <AuthField label="닉네임" htmlFor="signup-nickname" error={error} hint={<span aria-live="polite">{hint}</span>}>
        <div className="relative">
          <AuthInput
            id="signup-nickname"
            autoComplete="nickname"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            placeholder="닉네임을 입력해 주세요"
            value={nickname}
            onChange={(e) => onChange(e.target.value)}
            invalid={!!error}
            aria-describedby={error ? "signup-nickname-error" : undefined}
            className="pr-12"
          />
          <StatusIcon status={status} />
        </div>
      </AuthField>
    </SignupScreen>
  );
}
