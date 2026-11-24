import { API_BASE } from "@/shared/api/http";

/** 지원하는 소셜 로그인. 값은 API · DB(`user_social_accounts.provider`)와 같은 대문자입니다. */
export const SOCIAL_PROVIDERS = ["KAKAO", "GOOGLE"] as const;
export type SocialProvider = (typeof SOCIAL_PROVIDERS)[number];

export const SOCIAL_PROVIDER_LABEL: Record<SocialProvider, string> = {
  KAKAO: "카카오",
  GOOGLE: "Google",
};

/** 주소에 쓰는 제공자 이름(Spring Security registrationId): kakao · google */
export function providerSlug(provider: SocialProvider): string {
  return provider.toLowerCase();
}

/** 주소 · 쿼리의 제공자 이름(kakao, GOOGLE 등)을 읽습니다. 모르는 값이면 null */
export function parseSocialProvider(value: string | null | undefined): SocialProvider | null {
  const upper = value?.trim().toUpperCase();
  return SOCIAL_PROVIDERS.find((p) => p === upper) ?? null;
}

/**
 * 소셜 로그인 시작 주소. fetch가 아니라 페이지 이동으로 엽니다.
 * 서버가 state를 만들어 제공자 동의 화면으로 보내고, 콜백에서 기존 회원이면 피드로, 처음이면 온보딩(/signup)으로 돌려보냅니다.
 * 계정 추가 연결은 이 주소를 쓰지 않고, 로그인한 상태에서 서버가 내려준 주소(authorizationUrl)로 이동합니다.
 */
export function oauthAuthorizeUrl(provider: SocialProvider): string {
  return `${API_BASE}/auth/oauth2/authorization/${providerSlug(provider)}`;
}
