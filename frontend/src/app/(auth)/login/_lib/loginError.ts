import { parseSocialProvider, SOCIAL_PROVIDER_LABEL } from "@/shared/auth/socialProviders";

/**
 * 로그인 화면으로 돌아올 때 붙는 사유 코드(`/login?error=`)와 안내 문구.
 * 소셜 로그인 콜백 실패는 백엔드가, 세션 만료는 프론트(QueryClient)가 붙입니다.
 */
const MESSAGES: Record<string, string> = {
  /** 동의 화면에서 취소 */
  OAUTH_CANCELLED: "로그인을 취소했어요. 다시 시도하려면 아래 버튼을 눌러 주세요.",
  /** state 불일치, 제공자 오류, 토큰 교환 실패 등 */
  OAUTH_FAILED: "소셜 계정 인증에 실패했어요. 잠시 후 다시 시도해 주세요.",
  /** 온보딩을 마치기 전에 임시 가입 세션이 만료됨 */
  SIGNUP_SESSION_EXPIRED: "가입 시간이 지났어요. 처음부터 다시 진행해 주세요.",
  /** 재발급까지 실패(만료 · 로그아웃 · 다른 곳에서 폐기) */
  SESSION_EXPIRED: "로그인이 만료됐어요. 다시 로그인해 주세요.",
  /** 정지 · 탈퇴한 계정 (백엔드는 사유를 나누지 않습니다) */
  ACCOUNT_UNAVAILABLE: "이용할 수 없는 계정이에요. 자세한 내용은 고객센터로 문의해 주세요.",
  /** 처음 온 소셜 계정의 이메일이 이미 다른 로그인 수단으로 가입돼 있음. 가입한 수단을 알면 아래 함수가 이름을 넣습니다. */
  EMAIL_ALREADY_REGISTERED: "이미 다른 로그인 수단으로 가입된 이메일이에요. 처음 가입한 수단으로 로그인해 주세요.",
};

/**
 * 사유 코드가 없으면 null, 모르는 코드면 일반 인증 실패 문구.
 * @param provider 백엔드가 함께 붙이는 `&provider=`(EMAIL_ALREADY_REGISTERED일 때 그 이메일로 가입한 수단: kakao · google)
 */
export function loginErrorMessage(code: string | null, provider?: string | null): string | null {
  if (!code) return null;
  const registered = code === "EMAIL_ALREADY_REGISTERED" ? parseSocialProvider(provider) : null;
  if (registered) {
    const label = SOCIAL_PROVIDER_LABEL[registered];
    return `이미 ${label}로 가입된 이메일이에요. ${label}로 로그인해 주세요.`;
  }
  return MESSAGES[code] ?? MESSAGES.OAUTH_FAILED;
}
