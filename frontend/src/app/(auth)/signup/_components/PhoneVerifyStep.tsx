"use client";

import { ShieldCheck } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { cn } from "@/shared/lib/cn";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { AuthField, AuthHeading, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { CODE_LENGTH, CODE_TTL_SECONDS, formatCountdown, formatPhone, isValidPhone, normalizeCode } from "../_lib/phone";

/**
 * 회원가입 마지막 단계: 휴대폰 번호로 인증번호를 받아 확인합니다.
 * 껍데기 단계: 문자 발송·확인 API가 아직 없어 6자리를 입력하면 통과합니다.
 */
export function PhoneVerifyStep({ onBack, onVerified }: { onBack: () => void; onVerified: () => void }) {
  const toast = useToast();
  const [phone, setPhone] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [remaining, setRemaining] = useState(0);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);

  const expired = sentTo !== null && remaining === 0;
  const phoneChanged = sentTo !== null && sentTo !== phone;

  // 남은 시간이 있는 동안만 1초마다 줄입니다. 다시 받으면 remaining이 초기화되고 타이머는 그대로 이어집니다.
  const ticking = remaining > 0;
  useEffect(() => {
    if (!ticking) return;
    const id = setInterval(() => setRemaining((r) => Math.max(0, r - 1)), 1000);
    return () => clearInterval(id);
  }, [ticking]);

  const send = () => {
    if (!isValidPhone(phone)) return;
    setSentTo(phone);
    setRemaining(CODE_TTL_SECONDS);
    setCode("");
    setCodeError(null);
    toast({ message: "인증번호를 보냈어요." });
  };

  const verify = (e: FormEvent) => {
    e.preventDefault();
    if (expired) return setCodeError("인증 시간이 지났어요. 인증번호를 다시 받아 주세요.");
    if (code.length !== CODE_LENGTH) return setCodeError(`인증번호 ${CODE_LENGTH}자리를 입력해 주세요.`);
    onVerified();
  };

  const showCode = sentTo !== null && !phoneChanged;

  return (
    <div className="min-h-dvh">
      <TopBar title="휴대폰 인증" onBack={onBack} />
      <form onSubmit={verify} noValidate className="px-5 pt-6 pb-10">
        <AuthHeading title={<>휴대폰 번호를<br />인증해 주세요</>} description="안전한 찜콩 이용을 위해 본인 확인을 한 번만 진행할게요." />

        <div className="mt-8 space-y-6">
          <AuthField
            label="휴대폰 번호"
            htmlFor="signup-phone"
            hint={phoneChanged ? "번호가 바뀌었어요. 인증번호를 다시 받아 주세요." : undefined}
          >
            <div className="flex gap-2">
              <AuthInput
                id="signup-phone"
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder="010-0000-0000"
                value={phone}
                onChange={(e) => setPhone(formatPhone(e.target.value))}
                className="flex-1"
              />
              <button
                type="button"
                onClick={send}
                disabled={!isValidPhone(phone)}
                className="h-14 shrink-0 rounded-[14px] border border-primary-deep/20 bg-soft px-4 text-[15px] font-bold text-primary-deep transition-colors active:bg-selected disabled:cursor-not-allowed disabled:border-transparent disabled:bg-fill disabled:text-ink-faint"
              >
                {sentTo === null || phoneChanged ? "인증번호 받기" : "다시 받기"}
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
        </div>

        <div className="mt-8 flex gap-3 rounded-[16px] bg-soft px-5 py-4">
          <ShieldCheck size={20} className="mt-0.5 shrink-0 text-primary-deep" aria-hidden />
          <div>
            <p className="text-[15px] font-bold text-ink">번호는 본인 확인에만 사용해요</p>
            <p className="mt-1 text-[14px] leading-relaxed text-ink-sub">입력한 번호는 다른 사람에게 공개되지 않아요.</p>
          </div>
        </div>

        <button type="submit" disabled={!showCode || code.length !== CODE_LENGTH} className={`${pillButtonClass} mt-8`}>
          인증 완료
        </button>
      </form>
    </div>
  );
}
