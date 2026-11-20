"use client";

import { useMutation } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { TopBar } from "@/shared/ui/layout";
import { AuthField, AuthHeading, AuthInput, pillButtonClass } from "../../../_components/AuthUi";
import { emailError, passwordError } from "../../../_lib/validation";
import { passwordResetApi } from "../_api/passwordResetApi";

type Step = { name: "email" } | { name: "code"; requestId: string; sentTo: string } | { name: "password"; requestId: string } | { name: "done" };

/** 비밀번호 재설정: 이메일 → 인증번호 → 새 비밀번호 → 완료 */
export function PasswordResetFlow() {
  const [step, setStep] = useState<Step>({ name: "email" });
  const back = () => {
    if (step.name === "code") setStep({ name: "email" });
    else if (step.name === "password") setStep({ name: "email" });
  };

  return (
    <div className="min-h-dvh">
      <TopBar title="비밀번호 재설정" backHref={routes.login()} onBack={step.name === "code" || step.name === "password" ? back : undefined} />
      <div className="px-5 pt-6 pb-10">
        {step.name === "email" && <EmailStep onSent={(requestId, sentTo) => setStep({ name: "code", requestId, sentTo })} />}
        {step.name === "code" && (
          <CodeStep requestId={step.requestId} sentTo={step.sentTo} onVerified={() => setStep({ name: "password", requestId: step.requestId })} />
        )}
        {step.name === "password" && <PasswordStep requestId={step.requestId} onDone={() => setStep({ name: "done" })} />}
        {step.name === "done" && <DoneStep />}
      </div>
    </div>
  );
}

function ErrorLine({ error }: { error: unknown }) {
  return (
    <p className="mt-4 text-[13px] text-danger" role="alert">
      {errorMessage(error)}
    </p>
  );
}

function EmailStep({ onSent }: { onSent: (requestId: string, sentTo: string) => void }) {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const request = useMutation({ mutationFn: passwordResetApi.request, onSuccess: (r) => onSent(r.requestId, r.sentTo) });
  const error = submitted ? emailError(email) : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!emailError(email)) request.mutate(email.trim());
  };

  return (
    <form onSubmit={submit} noValidate>
      <AuthHeading title={<>가입한 이메일을<br />알려 주세요</>} description="비밀번호를 다시 정할 수 있도록 인증번호를 보내 드릴게요." />
      <div className="mt-8">
        <AuthField label="이메일" htmlFor="reset-email" error={error}>
          <AuthInput
            id="reset-email"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="이메일 주소를 입력해 주세요"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            invalid={!!error}
          />
        </AuthField>
      </div>
      {request.isError && <ErrorLine error={request.error} />}
      <button type="submit" disabled={request.isPending} className={`${pillButtonClass} mt-8`}>
        {request.isPending ? "보내는 중…" : "인증번호 받기"}
      </button>
    </form>
  );
}

function CodeStep({ requestId, sentTo, onVerified }: { requestId: string; sentTo: string; onVerified: () => void }) {
  const [code, setCode] = useState("");
  const verify = useMutation({ mutationFn: () => passwordResetApi.verify(requestId, code), onSuccess: onVerified });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (code.length === 6) verify.mutate();
      }}
      noValidate
    >
      <AuthHeading title="인증번호를 입력해 주세요" description={`${sentTo}로 6자리 인증번호를 보냈어요.`} />
      <div className="mt-8">
        <AuthField label="인증번호" htmlFor="reset-code" hint="메일이 오지 않으면 스팸함을 확인해 주세요.">
          <AuthInput
            id="reset-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="인증번호 6자리"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
            className="tracking-[2px]"
          />
        </AuthField>
      </div>
      {verify.isError && <ErrorLine error={verify.error} />}
      <button type="submit" disabled={code.length !== 6 || verify.isPending} className={`${pillButtonClass} mt-8`}>
        {verify.isPending ? "확인 중…" : "확인"}
      </button>
    </form>
  );
}

function PasswordStep({ requestId, onDone }: { requestId: string; onDone: () => void }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const save = useMutation({ mutationFn: () => passwordResetApi.confirm(requestId, password), onSuccess: onDone });
  const pwError = submitted ? passwordError(password) : null;
  const confirmError = submitted && confirm !== password ? "비밀번호가 서로 달라요." : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!passwordError(password) && confirm === password) save.mutate();
  };

  return (
    <form onSubmit={submit} noValidate>
      <AuthHeading title="새 비밀번호를 정해 주세요" description="이전에 쓰던 비밀번호와 다르게 정하면 더 안전해요." />
      <div className="mt-8 space-y-6">
        <AuthField label="새 비밀번호" htmlFor="reset-password" error={pwError} hint="영문·숫자를 포함한 8자 이상으로 설정해 주세요.">
          <AuthInput
            id="reset-password"
            type="password"
            autoComplete="new-password"
            placeholder="새 비밀번호를 입력해 주세요"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            invalid={!!pwError}
          />
        </AuthField>
        <AuthField label="새 비밀번호 확인" htmlFor="reset-confirm" error={confirmError}>
          <AuthInput
            id="reset-confirm"
            type="password"
            autoComplete="new-password"
            placeholder="한 번 더 입력해 주세요"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            invalid={!!confirmError}
          />
        </AuthField>
      </div>
      {save.isError && <ErrorLine error={save.error} />}
      <button type="submit" disabled={save.isPending} className={`${pillButtonClass} mt-8`}>
        {save.isPending ? "저장 중…" : "비밀번호 변경"}
      </button>
    </form>
  );
}

function DoneStep() {
  return (
    <div className="flex flex-col items-center pt-16 text-center">
      <div className="flex size-20 items-center justify-center rounded-full bg-soft">
        <CheckCircle2 size={38} className="text-primary-deep" aria-hidden />
      </div>
      <h2 className="mt-6 text-[24px] font-bold">비밀번호를 바꿨어요</h2>
      <p className="mt-2 text-[15px] text-ink-sub">새 비밀번호로 다시 로그인해 주세요.</p>
      <Link href={routes.login()} className={`${pillButtonClass} mt-10`}>
        로그인하러 가기
      </Link>
    </div>
  );
}
