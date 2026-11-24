import { SOCIAL_PROVIDERS, type SocialProvider } from "@/shared/auth/socialProviders";

/** 연결 화면의 한 줄: 제공자마다 연결 여부와 해제 가능 여부 */
export interface LoginMethodRow<A extends { provider: SocialProvider; canUnlink: boolean }> {
  provider: SocialProvider;
  account: A | null;
  /** 서버가 정한 해제 가능 여부. 연결되지 않았으면 false */
  canUnlink: boolean;
  /** 가입할 때 사용한 수단: 연결돼 있지만 해제할 수 없는 수단. '가입한 수단' 배지를 달고 해제 버튼을 보여 주지 않습니다. */
  isSignupMethod: boolean;
}

/**
 * 제공자마다 한 줄씩(카카오 · Google 순서) 만듭니다.
 * 해제 가능 여부는 화면에서 계산하지 않고 서버의 canUnlink를 그대로 씁니다. 규칙(가입 수단은 해제 불가)은 서버가 정합니다.
 */
export function loginMethodRows<A extends { provider: SocialProvider; canUnlink: boolean }>(accounts: A[]): LoginMethodRow<A>[] {
  return SOCIAL_PROVIDERS.map((provider) => {
    const account = accounts.find((a) => a.provider === provider) ?? null;
    const canUnlink = account?.canUnlink ?? false;
    return { provider, account, canUnlink, isSignupMethod: account !== null && !canUnlink };
  });
}

/**
 * 계정 연결 콜백이 붙여 돌려보내는 사유 코드(`?error=`)와 안내 문구.
 * 연결 해제 API의 409 SIGNUP_METHOD_CANNOT_BE_UNLINKED는 서버 message를 그대로 씁니다.
 */
const LINK_ERRORS: Record<string, string> = {
  /** 이 소셜 계정이 이미 다른 찜콩 계정에 연결돼 있음 */
  SOCIAL_ACCOUNT_ALREADY_LINKED: "이 소셜 계정은 이미 다른 찜콩 계정에 연결돼 있어요. 그 계정에서 연결을 해제한 뒤 다시 시도해 주세요.",
  /** 같은 제공자의 다른 계정이 이미 내 계정에 연결돼 있음 (제공자마다 하나만 연결) */
  PROVIDER_ALREADY_LINKED: "이 로그인 수단은 이미 다른 계정으로 연결돼 있어요. 연결을 해제한 뒤 다시 시도해 주세요.",
  /** 연결하려는 소셜 계정의 이메일이 다른 찜콩 회원의 것 (1인 1계정) */
  EMAIL_ALREADY_REGISTERED: "이 소셜 계정의 이메일은 다른 찜콩 계정에서 쓰고 있어서 연결할 수 없어요.",
  /** 내 계정이 정지 · 탈퇴 상태 */
  ACCOUNT_UNAVAILABLE: "지금은 이 계정에 로그인 수단을 연결할 수 없어요. 자세한 내용은 고객센터로 문의해 주세요.",
  /** 연결 주소가 만료 · 위조되었거나, 로그인 상태가 바뀜(로그아웃 · 다른 계정으로 로그인) */
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
