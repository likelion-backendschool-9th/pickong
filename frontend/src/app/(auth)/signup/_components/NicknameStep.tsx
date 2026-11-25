"use client";

import type { FormEvent } from "react";
import {
  NICKNAME_MAX,
  NicknameStatusIcon,
  nicknameFeedback,
  normalizeNicknameInput,
  useNicknameCheck,
} from "@/entities/user";
import { AuthField, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { SignupScreen, type StepFrame } from "./SignupUi";

/**
 * 1단계: 아이디. 영문만 쓸 수 있고 중복될 수 없으며, 프로필 주소(/users/아이디)로 쓰입니다.
 * 입력이 멈추면 바로 형식과 중복 여부를 확인해 입력칸 옆에 표시하고, 사용할 수 있을 때만 다음으로 넘어갑니다.
 * 가입 완료 시점에 서버가 한 번 더 확인하므로, 그 사이 다른 사람이 가져가면 이 단계로 돌아와 ‘사용 중’으로 표시됩니다.
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
  const status = useNicknameCheck(nickname);
  const { error, hint: hintText } = nicknameFeedback(nickname, status);
  const hint = status === "available" ? <span className="text-primary-deep">{hintText}</span> : hintText;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (status === "available") onNext();
  };

  return (
    <SignupScreen
      frame={frame}
      heading={
        <>
          찜콩에서 쓸
          <br />
          아이디를 정해 주세요
        </>
      }
      description="아이디는 피드 · 댓글에 보이는 내 이름이자 프로필 주소로 쓰여요."
      onSubmit={submit}
      footer={
        <button type="submit" disabled={status !== "available"} className={pillButtonClass}>
          다음
        </button>
      }
    >
      <AuthField label="아이디" htmlFor="signup-nickname" error={error} hint={<span aria-live="polite">{hint}</span>}>
        <div className="relative">
          <span className="pointer-events-none absolute top-1/2 left-5 -translate-y-1/2 text-[16px] text-ink-faint" aria-hidden>
            @
          </span>
          <AuthInput
            id="signup-nickname"
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            inputMode="email"
            lang="en"
            maxLength={NICKNAME_MAX}
            placeholder="pickong"
            value={nickname}
            onChange={(e) => onChange(normalizeNicknameInput(e.target.value))}
            invalid={!!error}
            aria-describedby={error ? "signup-nickname-error" : undefined}
            className="pr-12 pl-10"
          />
          <NicknameStatusIcon status={status} />
        </div>
      </AuthField>
    </SignupScreen>
  );
}
