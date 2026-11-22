"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/shared/api/errors";
import { authApi } from "@/shared/auth/authApi";
import { routes } from "@/shared/config/routes";
import { useToast } from "@/shared/ui/Toast";
import { AuthCheckbox, AuthField, AuthHeading, AuthInput, AuthSwitchLine, pillButtonClass } from "../../_components/AuthUi";

/** 로그인 폼. 모바일 한 화면 안에 들어가도록 회원가입 안내를 화면 맨 아래에 붙입니다. */
export function LoginForm() {
  const router = useRouter();
  const toast = useToast();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [keepSignedIn, setKeepSignedIn] = useState(true);
  const [submitted, setSubmitted] = useState(false);
  const login = useMutation({
    mutationFn: authApi.login,
    // 관리자 계정은 관리 페이지로, 회원은 피드로 보냅니다.
    onSuccess: ({ role }) => router.replace(role === "ADMIN" ? routes.admin() : routes.feed()),
  });

  // 로그인은 빈칸만 확인하고, 이메일인지 닉네임인지와 계정이 맞는지는 서버가 판단합니다.
  const loginIdErr = submitted && !loginId.trim() ? "이메일 또는 닉네임을 입력해 주세요." : null;
  const passwordErr = submitted && !password ? "비밀번호를 입력해 주세요." : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!loginId.trim() || !password) return;
    login.mutate({ loginId: loginId.trim(), password, keepSignedIn });
  };

  const comingSoon = () => toast({ message: "준비 중인 기능이에요." });

  return (
    <div className="flex flex-1 flex-col px-5 pt-4 pb-[calc(24px+env(safe-area-inset-bottom))]">
      <p className="text-[28px] leading-none font-black tracking-[-1px] text-ink short:hidden">찜콩</p>
      <AuthHeading className="mt-5 short:mt-1" title="다시 만나 반가워요" description="찜해 둔 장소와 나만의 일정을 이어가세요." />

      <form onSubmit={submit} noValidate className="mt-8 short:mt-6">
        <div className="space-y-4">
          <AuthField label="이메일 / 닉네임" htmlFor="login-id" error={loginIdErr}>
            <AuthInput
              id="login-id"
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              placeholder="이메일 또는 닉네임을 입력해 주세요"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              invalid={!!loginIdErr}
              aria-describedby={loginIdErr ? "login-id-error" : undefined}
            />
          </AuthField>
          <AuthField label="비밀번호" htmlFor="login-password" error={passwordErr}>
            <AuthInput
              id="login-password"
              type="password"
              autoComplete="current-password"
              placeholder="비밀번호를 입력해 주세요"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              invalid={!!passwordErr}
              aria-describedby={passwordErr ? "login-password-error" : undefined}
            />
          </AuthField>
        </div>

        <AuthCheckbox className="mt-4 text-[15px] text-ink-sub" checked={keepSignedIn} onChange={setKeepSignedIn}>
          로그인 상태 유지
        </AuthCheckbox>

        {login.isError && (
          <p className="mt-3 text-[13px] text-danger" role="alert">
            {errorMessage(login.error)}
          </p>
        )}

        <button type="submit" disabled={login.isPending} aria-busy={login.isPending || undefined} className={`${pillButtonClass} mt-5`}>
          {login.isPending ? "로그인 중…" : "로그인"}
        </button>
      </form>

      <div className="mt-4 flex items-center justify-center gap-5 text-[15px] text-ink-sub">
        <button type="button" onClick={comingSoon} className="py-1">
          이메일 찾기
        </button>
        <span className="h-4 w-px bg-line" aria-hidden />
        <Link href={routes.passwordReset()} className="py-1">
          비밀번호 찾기
        </Link>
      </div>

      <div className="mt-auto pt-8 short:pt-5">
        <AuthSwitchLine>
          아직 찜콩 회원이 아니신가요?
          <Link href={routes.signup()} className="ml-3 font-bold text-ink">
            회원가입
          </Link>
        </AuthSwitchLine>
      </div>
    </div>
  );
}
