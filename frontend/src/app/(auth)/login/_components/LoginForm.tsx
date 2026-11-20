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

export function LoginForm() {
  const router = useRouter();
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [keepSignedIn, setKeepSignedIn] = useState(true);
  const [submitted, setSubmitted] = useState(false);
  const login = useMutation({ mutationFn: authApi.login, onSuccess: () => router.replace(routes.feed()) });

  // 로그인은 빈칸만 확인하고, 계정이 맞는지는 서버가 판단합니다. (이메일 형식 검사는 회원가입에서)
  const emailErr = submitted && !email.trim() ? "이메일을 입력해 주세요." : null;
  const passwordErr = submitted && !password ? "비밀번호를 입력해 주세요." : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!email.trim() || !password) return;
    login.mutate({ email: email.trim(), password, keepSignedIn });
  };

  const comingSoon = () => toast({ message: "준비 중인 기능이에요." });

  return (
    <div className="px-5 pt-8 pb-10">
      <p className="text-[34px] leading-none font-black tracking-[-1px] text-ink">찜콩</p>
      <AuthHeading className="mt-7" title="다시 만나 반가워요" description="찜해 둔 장소와 나만의 일정을 이어가세요." />

      <form onSubmit={submit} noValidate className="mt-10">
        <div className="space-y-6">
          <AuthField label="이메일" htmlFor="login-email" error={emailErr}>
            <AuthInput
              id="login-email"
              type="email"
              inputMode="email"
              autoComplete="email"
              placeholder="이메일 주소를 입력해 주세요"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              invalid={!!emailErr}
              aria-describedby={emailErr ? "login-email-error" : undefined}
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

        <AuthCheckbox className="mt-6 text-[15px] text-ink-sub" checked={keepSignedIn} onChange={setKeepSignedIn}>
          로그인 상태 유지
        </AuthCheckbox>

        {login.isError && (
          <p className="mt-4 text-[13px] text-danger" role="alert">
            {errorMessage(login.error)}
          </p>
        )}

        <button type="submit" disabled={login.isPending} aria-busy={login.isPending || undefined} className={`${pillButtonClass} mt-6`}>
          {login.isPending ? "로그인 중…" : "로그인"}
        </button>
      </form>

      <div className="mt-6 flex items-center justify-center gap-5 text-[15px] text-ink-sub">
        <button type="button" onClick={comingSoon} className="py-1">
          이메일 찾기
        </button>
        <span className="h-4 w-px bg-line" aria-hidden />
        <button type="button" onClick={comingSoon} className="py-1">
          비밀번호 찾기
        </button>
      </div>

      <div className="mt-10 rounded-[16px] bg-soft px-6 py-5">
        <p className="text-[16px] font-bold text-ink">가고 싶은 곳, 잊지 않도록</p>
        <p className="mt-2 text-[15px] leading-relaxed text-ink-sub">
          발견한 장소를 찜콩하고
          <br />내 지도와 일정으로 한 번에 모아보세요.
        </p>
      </div>

      <div className="mt-9">
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
