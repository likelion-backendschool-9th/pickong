import { SocialLogo } from "@/shared/auth/SocialLogo";
import { oauthAuthorizeUrl, SOCIAL_PROVIDER_LABEL, type SocialProvider } from "@/shared/auth/socialProviders";
import { cn } from "@/shared/lib/cn";

/** 카카오는 카카오 로그인 가이드의 노란 버튼, Google은 흰 바탕 + 회색 테두리 버튼 */
const STYLE: Record<SocialProvider, string> = {
  KAKAO: "bg-kakao text-kakao-ink",
  GOOGLE: "border border-line bg-bg text-ink",
};

/**
 * “카카오로 계속하기” · “Google로 계속하기”. 로그인과 가입을 나누지 않고 같은 버튼을 씁니다.
 * API 주소로 페이지 이동해야 하므로 next/link가 아니라 <a>입니다.
 */
export function SocialLoginButton({ provider }: { provider: SocialProvider }) {
  return (
    <a
      href={oauthAuthorizeUrl(provider)}
      className={cn(
        "relative flex h-14 w-full items-center justify-center rounded-full text-[17px] font-bold transition active:brightness-95",
        STYLE[provider],
      )}
    >
      <span className="absolute left-6 flex items-center">
        <SocialLogo provider={provider} size={20} />
      </span>
      {SOCIAL_PROVIDER_LABEL[provider]}로 계속하기
    </a>
  );
}
