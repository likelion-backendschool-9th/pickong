import { NICKNAME_MAX, nicknameError, normalizeNicknameInput } from "@/entities/user";

/**
 * 최초 가입 온보딩: 단계 순서, 아이디 기본값, 약관 동의. (아이디 · 이름 규칙은 entities/user)
 * 소셜 인증을 마친 신규 회원만 들어오며, 비밀번호 · 전화번호는 받지 않습니다.
 * 검증 함수는 오류가 없으면 null을 돌려줍니다.
 */

/**
 * nickname(아이디, 실시간 중복 확인) → profile(프로필 사진 · 이름, 선택이라 건너뛸 수 있음)
 * → terms(프로필 화면 위에 올라오는 약관 동의 시트)
 */
export const SIGNUP_STEPS = ["nickname", "profile", "terms"] as const;
export type SignupStep = (typeof SIGNUP_STEPS)[number];

/** 1부터 시작하는 단계 번호 */
export function stepNumber(step: SignupStep): number {
  return SIGNUP_STEPS.indexOf(step) + 1;
}

/** 이전 단계. 첫 단계면 null(가입을 그만둘지 묻습니다) */
export function previousStep(step: SignupStep): SignupStep | null {
  const index = SIGNUP_STEPS.indexOf(step);
  return index > 0 ? SIGNUP_STEPS[index - 1] : null;
}

// ── 아이디: 규칙과 중복 확인 상태는 프로필 편집과 함께 쓰므로 entities/user/nickname.ts에 있습니다. ──

/** 소셜 계정 이메일의 앞부분이 규칙에 맞으면 아이디 기본값으로 씁니다. 맞지 않으면 빈칸 */
export function suggestedNickname(email: string | null | undefined): string {
  const local = normalizeNicknameInput(email?.split("@")[0] ?? "").slice(0, NICKNAME_MAX);
  return nicknameError(local) ? "" : local;
}

// ── 약관 동의 ──

export type AgreementKey = "terms" | "privacy" | "marketing";
export type Agreements = Record<AgreementKey, boolean>;
/** API · DB(`terms.type`) 값 */
export type TermsType = "SERVICE" | "PRIVACY" | "MARKETING";

export const AGREEMENT_ITEMS: {
  key: AgreementKey;
  label: string;
  required: boolean;
  type: TermsType;
  termsType: "service" | "privacy" | "marketing";
}[] = [
  { key: "terms", label: "서비스 이용약관 동의", required: true, type: "SERVICE", termsType: "service" },
  { key: "privacy", label: "개인정보 수집 및 이용 동의", required: true, type: "PRIVACY", termsType: "privacy" },
  { key: "marketing", label: "마케팅 정보 수신 동의", required: false, type: "MARKETING", termsType: "marketing" },
];

export const NO_AGREEMENTS: Agreements = { terms: false, privacy: false, marketing: false };

export function isAllAgreed(agreements: Agreements): boolean {
  return AGREEMENT_ITEMS.every((item) => agreements[item.key]);
}

export function hasRequiredAgreements(agreements: Agreements): boolean {
  return AGREEMENT_ITEMS.every((item) => !item.required || agreements[item.key]);
}

/** ‘모두 동의’: 하나라도 빠져 있으면 전부 켜고, 모두 켜져 있으면 전부 끕니다. */
export function toggleAllAgreements(agreements: Agreements): Agreements {
  const next = !isAllAgreed(agreements);
  return { terms: next, privacy: next, marketing: next };
}

/** 가입 요청에 담을 동의한 약관 목록. 서버는 지금 시행 중인 버전으로 동의 기록을 남깁니다. */
export function agreedTermsTypes(agreements: Agreements): TermsType[] {
  return AGREEMENT_ITEMS.filter((item) => agreements[item.key]).map((item) => item.type);
}

// ── 가입 완료 요청이 실패했을 때 돌아갈 단계 ──

/** 오류 코드에 맞는 단계로 돌려보냅니다. 해당 단계가 없는 오류(만료 등)는 null */
export function stepForSignupError(code: string): SignupStep | null {
  switch (code) {
    case "INVALID_USERNAME":
    case "USERNAME_TAKEN":
      return "nickname";
    case "INVALID_NAME":
      return "profile";
    case "REQUIRED_TERMS_NOT_AGREED":
      return "terms";
    default:
      return null;
  }
}
