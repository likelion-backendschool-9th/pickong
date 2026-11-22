"use client";

import { useState, type FormEvent } from "react";
import { AuthField, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { passwordConfirmError, passwordError } from "../../_lib/validation";
import { SignupScreen, type StepFrame } from "./SignupUi";

type FieldName = "password" | "confirm";

/**
 * 2단계: 비밀번호 + 비밀번호 확인. 두 가입 방법이 같은 화면을 씁니다.
 * - 전화번호 가입: 이메일을 받지 않으므로 닉네임과 이 비밀번호로 로그인합니다.
 * - 이메일 가입: 1단계에서 인증한 이메일(verifiedEmail) 또는 닉네임과 이 비밀번호로 로그인합니다.
 */
export function PasswordStep({
  frame,
  verifiedEmail,
  password,
  confirm,
  onChange,
  onNext,
}: {
  frame: StepFrame;
  verifiedEmail: string | null;
  password: string;
  confirm: string;
  onChange: (patch: { password?: string; passwordConfirm?: string }) => void;
  onNext: () => void;
}) {
  const [touched, setTouched] = useState<Record<FieldName, boolean>>({ password: false, confirm: false });
  const [submitted, setSubmitted] = useState(false);

  const errors: Record<FieldName, string | null> = {
    password: passwordError(password),
    confirm: passwordConfirmError(password, confirm),
  };
  const shown = (name: FieldName) => (touched[name] || submitted ? errors[name] : null);
  const fieldProps = (name: FieldName) => ({
    id: `signup-${name}`,
    type: "password",
    autoComplete: "new-password",
    invalid: !!shown(name),
    onBlur: () => setTouched((t) => ({ ...t, [name]: true })),
    "aria-describedby": shown(name) ? `signup-${name}-error` : undefined,
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (errors.password || errors.confirm) return;
    onNext();
  };

  return (
    <SignupScreen
      frame={frame}
      heading={<>비밀번호를<br />설정해 주세요</>}
      description={verifiedEmail ? `${verifiedEmail} 또는 닉네임으로 로그인할 때 써요.` : "닉네임과 이 비밀번호로 로그인해요."}
      onSubmit={submit}
      footer={
        <button type="submit" className={pillButtonClass}>
          다음
        </button>
      }
    >
      <AuthField label="비밀번호" htmlFor="signup-password" error={shown("password")} hint="영문·숫자를 포함한 8자 이상으로 설정해 주세요.">
        <AuthInput
          {...fieldProps("password")}
          placeholder="비밀번호를 입력해 주세요"
          value={password}
          onChange={(e) => onChange({ password: e.target.value })}
        />
      </AuthField>
      <AuthField label="비밀번호 확인" htmlFor="signup-confirm" error={shown("confirm")}>
        <AuthInput
          {...fieldProps("confirm")}
          placeholder="비밀번호를 한 번 더 입력해 주세요"
          value={confirm}
          onChange={(e) => onChange({ passwordConfirm: e.target.value })}
        />
      </AuthField>
    </SignupScreen>
  );
}
