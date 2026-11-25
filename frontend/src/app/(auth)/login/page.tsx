import type { Metadata } from "next";
import { Suspense } from "react";
import { SOCIAL_PROVIDERS } from "@/shared/auth/socialProviders";
import { routes } from "@/shared/config/routes";
import { TopBar } from "@/shared/ui/layout";
import { AuthHeading } from "../_components/AuthUi";
import { LoginErrorNotice } from "./_components/LoginErrorNotice";
import { LoginHero } from "./_components/LoginHero";
import { SocialLoginButton } from "./_components/SocialLoginButton";

export const metadata: Metadata = { title: "로그인 · 회원가입" };

/**
 * 로그인 · 회원가입 통합 화면. 카카오 · Google로만 시작합니다.
 * 이미 가입한 소셜 계정이면 바로 로그인되고, 처음이면 서버가 온보딩(/signup)으로 보냅니다.
 * 버튼은 페이지 이동 링크라서 이 화면은 서버에서 그리고, 실패 안내(?error=)만 클라이언트에서 읽습니다.
 */
export default function LoginPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="" backHref={routes.start()} />
      <div className="flex flex-1 flex-col px-5 pt-1 pb-[calc(24px+env(safe-area-inset-bottom))]">
        <LoginHero />

        <div className="mt-7 short:mt-4">
          {/* 워드마크: 피드 헤더와 같은 ‘찜콩’ + 연두 점 */}
          <p className="flex items-end text-[26px] leading-none font-black tracking-[-1.6px] text-ink short:hidden">
            찜콩
            <span className="mb-[3px] ml-[3px] size-[6px] rounded-full bg-primary" aria-hidden />
          </p>
          <AuthHeading
            className="mt-4 short:mt-0"
            title={
              <>
                가고 싶은 곳을 찜하고
                <br />
                <span className="text-primary-deep">내 지도</span>로 모아 보세요
              </>
            }
            description={
              <>
                쓰던 계정으로 바로 시작해요.
                <br />
                처음이라면 아이디만 정하면 돼요.
              </>
            }
          />
        </div>

        <div className="mt-auto pt-8 short:pt-5">
          <Suspense>
            <LoginErrorNotice />
          </Suspense>
          <div className="space-y-3">
            {SOCIAL_PROVIDERS.map((provider) => (
              <SocialLoginButton key={provider} provider={provider} />
            ))}
          </div>
          <p className="mt-4 flex items-center justify-center gap-1.5 text-center text-[13px] text-ink-sub">
            <span className="size-1.5 rounded-full bg-primary" aria-hidden />
            비밀번호 없이 소셜 계정으로 로그인해요
          </p>
        </div>
      </div>
    </div>
  );
}
