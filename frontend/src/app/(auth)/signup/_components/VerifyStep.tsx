"use client";

import { useEffect, useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import { useToast } from "@/shared/ui/Toast";
import { AuthField, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { emailError } from "../../_lib/validation";
import { formatPhone, isValidPhone } from "../_lib/phone";
import type { SignupMethod } from "../_lib/signup";
import { CODE_LENGTH, CODE_TTL_SECONDS, formatCountdown, normalizeCode } from "../_lib/verification";
import { MethodSwitchLink, SignupScreen, type StepFrame } from "./SignupUi";

interface Channel {
  heading: ReactNode;
  description: string;
  label: string;
  changedHint: string;
  input: InputHTMLAttributes<HTMLInputElement>;
  format: (value: string) => string;
  isValid: (value: string) => boolean;
}

const CHANNELS: Record<SignupMethod, Channel> = {
  phone: {
    heading: <>휴대폰 번호를<br />인증해 주세요</>,
    description: "번호는 본인 확인에만 쓰이고 공개되지 않아요.",
    label: "휴대폰 번호",
    changedHint: "번호가 바뀌었어요. 인증번호를 다시 받아 주세요.",
    input: { type: "tel", inputMode: "numeric", autoComplete: "tel-national", placeholder: "010-0000-0000" },
    format: formatPhone,
    isValid: isValidPhone,
  },
  email: {
    heading: <>이메일 주소를<br />인증해 주세요</>,
    description: "로그인과 계정 찾기에 쓸 이메일이에요.",
    label: "이메일",
    changedHint: "이메일이 바뀌었어요. 인증번호를 다시 받아 주세요.",
    input: { type: "email", inputMode: "email", autoComplete: "email", placeholder: "이메일 주소를 입력해 주세요" },
    format: (value) => value.replace(/\s/g, ""),
    isValid: (value) => !emailError(value),
  },
};

/**
 * 1단계: 휴대폰 번호 또는 이메일로 인증번호를 받아 확인합니다.
 * 껍데기 단계: 발송 · 확인 API가 아직 없어 6자리를 입력하면 통과합니다.
 * 인증을 마친 뒤 이 단계로 돌아오면, 번호(이메일)를 바꾸지 않는 한 다시 인증하지 않습니다.
 */
export function VerifyStep({
  frame,
  method,
  contact,
  verified,
  onContactChange,
  onVerified,
}: {
  frame: StepFrame;
  method: SignupMethod;
  contact: string;
  verified: boolean;
  onContactChange: (contact: string) => void;
  onVerified: () => void;
}) {
  const channel = CHANNELS[method];
  const toast = useToast();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [remaining, setRemaining] = useState(0);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);

  const valid = channel.isValid(contact);
  const expired = sentTo !== null && remaining === 0;
  const contactChanged = sentTo !== null && sentTo !== contact;
  const showCode = !verified && sentTo !== null && !contactChanged;

  // 남은 시간이 있는 동안만 1초마다 줄입니다. 다시 받으면 remaining이 초기화되고 타이머는 그대로 이어집니다.
  const ticking = remaining > 0;
  useEffect(() => {
    if (!ticking) return;
    const id = setInterval(() => setRemaining((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(id);
  }, [ticking]);

  const send = () => {
    if (!valid || verified) return;
    setSentTo(contact);
    setRemaining(CODE_TTL_SECONDS);
    setCode("");
    setCodeError(null);
    toast({ message: "인증번호를 보냈어요." });
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (verified) return onVerified();
    // 인증번호를 받기 전에 Enter를 누르면 인증번호를 보냅니다.
    if (!showCode) return send();
    if (expired) return setCodeError("인증 시간이 지났어요. 인증번호를 다시 받아 주세요.");
    if (code.length !== CODE_LENGTH) return setCodeError(`인증번호 ${CODE_LENGTH}자리를 입력해 주세요.`);
    onVerified();
  };

  const contactHint = verified ? "인증을 마쳤어요." : contactChanged ? channel.changedHint : undefined;

  return (
    <SignupScreen
      frame={frame}
      heading={channel.heading}
      description={channel.description}
      onSubmit={submit}
      footer={
        <>
          <button type="submit" disabled={!verified && (!showCode || code.length !== CODE_LENGTH)} className={pillButtonClass}>
            {verified ? "다음" : "인증 완료"}
          </button>
          <MethodSwitchLink to={method === "phone" ? "email" : "phone"} />
        </>
      }
    >
      <AuthField label={channel.label} htmlFor="signup-contact" hint={contactHint}>
        <div className="flex gap-2">
          <AuthInput
            id="signup-contact"
            {...channel.input}
            value={contact}
            onChange={(e) => onContactChange(channel.format(e.target.value))}
            className="flex-1"
          />
          <button
            type="button"
            onClick={send}
            disabled={!valid || verified}
            className="h-14 shrink-0 rounded-[14px] border border-primary-deep/20 bg-soft px-4 text-[15px] font-bold text-primary-deep transition-colors active:bg-selected disabled:cursor-not-allowed disabled:border-transparent disabled:bg-fill disabled:text-ink-faint"
          >
            {verified ? "인증 완료" : sentTo === null || contactChanged ? "인증번호 받기" : "다시 받기"}
          </button>
        </div>
      </AuthField>

      {showCode && (
        <AuthField label="인증번호" htmlFor="signup-code" error={codeError}>
          <div className="relative">
            <AuthInput
              id="signup-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder={`인증번호 ${CODE_LENGTH}자리`}
              value={code}
              onChange={(e) => {
                setCode(normalizeCode(e.target.value));
                setCodeError(null);
              }}
              invalid={!!codeError}
              aria-describedby={codeError ? "signup-code-error" : undefined}
              className="pr-20 tracking-[2px]"
            />
            <span
              className={cn(
                "pointer-events-none absolute top-1/2 right-5 -translate-y-1/2 text-[15px] font-medium tabular-nums",
                expired ? "text-danger" : "text-primary-deep",
              )}
              aria-label={`남은 시간 ${formatCountdown(remaining)}`}
            >
              {formatCountdown(remaining)}
            </span>
          </div>
          {!codeError && (
            <p className="mt-2 text-[13px] text-ink-sub">
              {expired ? "인증 시간이 지났어요. 인증번호를 다시 받아 주세요." : `${sentTo}로 인증번호를 보냈어요.`}
            </p>
          )}
        </AuthField>
      )}
    </SignupScreen>
  );
}
