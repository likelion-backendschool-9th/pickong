import { SOCIAL_PROVIDERS, type SocialProvider } from "@/shared/auth/socialProviders";

/** 연결 화면의 한 줄: 제공자마다 연결 여부와 해제 가능 여부 */
export interface LoginMethodRow<A extends { provider: SocialProvider }> {
  provider: SocialProvider;
  account: A | null;
  /** 연결돼 있고, 마지막 남은 로그인 수단이 아닐 때만 해제할 수 있습니다. */
  canUnlink: boolean;
}

export function loginMethodRows<A extends { provider: SocialProvider }>(accounts: A[]): LoginMethodRow<A>[] {
  const linkedCount = accounts.length;
  return SOCIAL_PROVIDERS.map((provider) => {
    const account = accounts.find((a) => a.provider === provider) ?? null;
    return { provider, account, canUnlink: account !== null && linkedCount > 1 };
  });
}

/**
 * 계정 연결 콜백이 붙여 돌려보내는 사유 코드(`?error=`)와 안내 문구.
 * 연결 해제 API의 409 LAST_LOGIN_METHOD는 서버 message를 그대로 씁니다.
 */
const LINK_ERRORS: Record<string, string> = {
  /** 이 소셜 계정이 이미 다른 찜콩 계정에 연결돼 있음 */
  SOCIAL_ACCOUNT_ALREADY_LINKED: "이 소셜 계정은 이미 다른 찜콩 계정에 연결돼 있어요. 그 계정에서 연결을 해제한 뒤 다시 시도해 주세요.",
  /** 같은 제공자의 다른 계정이 이미 내 계정에 연결돼 있음 (제공자마다 하나만 연결) */
  PROVIDER_ALREADY_LINKED: "이 로그인 수단은 이미 다른 계정으로 연결돼 있어요. 연결을 해제한 뒤 다시 시도해 주세요.",
  /** 1회용 연결 주소가 만료되었거나 이미 쓰임 */
  LINK_EXPIRED: "연결 시간이 지났어요. 다시 시도해 주세요.",
  OAUTH_CANCELLED: "계정 연결을 취소했어요.",
  OAUTH_FAILED: "소셜 계정 인증에 실패했어요. 잠시 후 다시 시도해 주세요.",
};

/** 사유가 없으면 null, 모르는 사유는 일반 인증 실패 문구 */
export function linkErrorMessage(code: string | null): string | null {
  if (!code) return null;
  return LINK_ERRORS[code] ?? LINK_ERRORS.OAUTH_FAILED;
}

/** 서버가 내려준 연결 주소가 같은 출처의 경로일 때만 이동합니다. (외부 주소로 보내는 응답을 막음) */
export function isSameOriginPath(url: string): boolean {
  return url.startsWith("/") && !url.startsWith("//");
}
