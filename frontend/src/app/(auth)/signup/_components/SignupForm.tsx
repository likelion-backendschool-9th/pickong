"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { routes } from "@/shared/config/routes";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { AuthCheckbox, AuthField, AuthHeading, AuthInput, AuthSwitchLine, pillButtonClass } from "../../_components/AuthUi";
import { emailError } from "../../_lib/validation";
import {
  AGREEMENT_ITEMS,
  NICKNAME_MAX,
  NICKNAME_MIN,
  hasRequiredAgreements,
  isAllAgreed,
  nicknameError,
  passwordError,
  toggleAllAgreements,
  type Agreements,
} from "../_lib/signup";

export interface SignupValue {
  nickname: string;
  email: string;
  password: string;
  agreements: Agreements;
}

type FieldName = "nickname" | "email" | "password";

export function SignupForm({
  value,
  onChange,
  onNext,
}: {
  value: SignupValue;
  onChange: (value: SignupValue) => void;
  onNext: () => void;
}) {
  const toast = useToast();
  const [touched, setTouched] = useState<Record<FieldName, boolean>>({ nickname: false, email: false, password: false });
  const [submitted, setSubmitted] = useState(false);

  const errors: Record<FieldName, string | null> = {
    nickname: nicknameError(value.nickname),
    email: emailError(value.email),
    password: passwordError(value.password),
  };
  const shown = (name: FieldName) => (touched[name] || submitted ? errors[name] : null);
  const agreementError = submitted && !hasRequiredAgreements(value.agreements) ? "필수 약관에 동의해 주세요." : null;

  const set = <K extends keyof SignupValue>(key: K, next: SignupValue[K]) => onChange({ ...value, [key]: next });
  const touch = (name: FieldName) => setTouched((t) => ({ ...t, [name]: true }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (errors.nickname || errors.email || errors.password || !hasRequiredAgreements(value.agreements)) return;
    onNext();
  };

  const inputProps = (name: FieldName) => ({
    id: `signup-${name}`,
    invalid: !!shown(name),
    onBlur: () => touch(name),
    "aria-describedby": shown(name) ? `signup-${name}-error` : undefined,
  });

  return (
    <div className="min-h-dvh">
      <TopBar title="회원가입" backHref={routes.login()} />
      <div className="px-5 pt-6 pb-10">
        <AuthHeading title="나만의 장소가 모이는 시작" description="찜콩과 함께 가고 싶은 순간을 모아보세요." />

        <form onSubmit={submit} noValidate className="mt-8">
          <div className="space-y-6">
            <AuthField label="닉네임" htmlFor="signup-nickname" error={shown("nickname")}>
              <AuthInput
                {...inputProps("nickname")}
                autoComplete="nickname"
                placeholder={`${NICKNAME_MIN}~${NICKNAME_MAX}자의 닉네임을 입력해 주세요`}
                value={value.nickname}
                onChange={(e) => set("nickname", e.target.value)}
              />
            </AuthField>
            <AuthField label="이메일" htmlFor="signup-email" error={shown("email")}>
              <AuthInput
                {...inputProps("email")}
                type="email"
                inputMode="email"
                autoComplete="email"
                placeholder="이메일 주소를 입력해 주세요"
                value={value.email}
                onChange={(e) => set("email", e.target.value)}
              />
            </AuthField>
            <AuthField
              label="비밀번호"
              htmlFor="signup-password"
              error={shown("password")}
              hint="영문·숫자를 포함한 8자 이상으로 설정해 주세요."
            >
              <AuthInput
                {...inputProps("password")}
                type="password"
                autoComplete="new-password"
                placeholder="비밀번호를 입력해 주세요"
                value={value.password}
                onChange={(e) => set("password", e.target.value)}
              />
            </AuthField>
          </div>

          <fieldset className="mt-8">
            <legend className="sr-only">약관 동의</legend>
            <div className="rounded-[14px] bg-soft px-5 py-4">
              <AuthCheckbox
                className="text-[16px] font-bold text-ink"
                checked={isAllAgreed(value.agreements)}
                onChange={() => set("agreements", toggleAllAgreements(value.agreements))}
              >
                약관에 모두 동의해요
              </AuthCheckbox>
            </div>
            <ul className="mt-3 space-y-1">
              {AGREEMENT_ITEMS.map((item) => (
                <li key={item.key} className="flex items-center justify-between gap-3 py-1">
                  <AuthCheckbox
                    className="text-[15px] text-ink-sub"
                    checked={value.agreements[item.key]}
                    onChange={(checked) => set("agreements", { ...value.agreements, [item.key]: checked })}
                  >
                    [{item.required ? "필수" : "선택"}] {item.label}
                  </AuthCheckbox>
                  {item.required && (
                    <button
                      type="button"
                      onClick={() => toast({ message: "약관 화면은 준비 중이에요." })}
                      className="shrink-0 px-1 py-1 text-[15px] text-ink-sub"
                      aria-label={`${item.label} 보기`}
                    >
                      보기
                    </button>
                  )}
                </li>
              ))}
            </ul>
            {agreementError && (
              <p className="mt-2 text-[13px] text-danger" role="alert">
                {agreementError}
              </p>
            )}
          </fieldset>

          <button type="submit" className={`${pillButtonClass} mt-6`}>
            동의하고 시작하기
          </button>
        </form>

        <div className="mt-6">
          <AuthSwitchLine>
            이미 찜콩 계정이 있으신가요?
            <Link href={routes.login()} className="ml-3 font-bold text-ink">
              로그인
            </Link>
          </AuthSwitchLine>
        </div>
      </div>
    </div>
  );
}
