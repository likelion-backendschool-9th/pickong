/** 회원가입 단계 순서, 입력 검증, 약관 동의 상태. 검증 함수는 오류가 없으면 null을 돌려줍니다. */

/** 가입 방법: 전화번호로 회원가입(기본) · 이메일로 회원가입 */
export type SignupMethod = "phone" | "email";

/**
 * 가입 단계. 두 방법 모두 같은 순서입니다.
 * verify(휴대폰 번호 또는 이메일 인증) → password(비밀번호 · 비밀번호 확인)
 * → nickname(실시간 중복 확인) → terms(닉네임 화면 위에 올라오는 약관 동의 시트)
 * 전화번호 가입은 이메일을 받지 않으며, 닉네임과 비밀번호로 로그인합니다.
 */
export const SIGNUP_STEPS = ["verify", "password", "nickname", "terms"] as const;
export type SignupStep = (typeof SIGNUP_STEPS)[number];

/** 1부터 시작하는 단계 번호 */
export function stepNumber(step: SignupStep): number {
  return SIGNUP_STEPS.indexOf(step) + 1;
}

/** 이전 단계. 첫 단계면 null(가입 화면을 떠남) */
export function previousStep(step: SignupStep): SignupStep | null {
  const index = SIGNUP_STEPS.indexOf(step);
  return index > 0 ? SIGNUP_STEPS[index - 1] : null;
}

export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 10;

export function nicknameError(nickname: string): string | null {
  const length = [...nickname.trim()].length;
  if (length === 0) return "닉네임을 입력해 주세요.";
  if (length < NICKNAME_MIN || length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`;
  // 로그인 칸은 이메일과 닉네임을 함께 받으므로, @가 있으면 이메일로 봅니다.
  if (nickname.includes("@")) return "닉네임에는 @를 쓸 수 없어요.";
  return null;
}

/**
 * 닉네임 입력칸 옆 표시 상태.
 * - idle: 비어 있음 / checking: 입력 중이거나 서버 확인 중 / invalid: 형식 오류
 * - available: 사용 가능(초록 체크) / taken: 이미 사용 중(빨간 X) / error: 확인 요청 실패
 */
export type NicknameStatus = "idle" | "checking" | "invalid" | "available" | "taken" | "error";

/**
 * @param nickname 지금 입력값
 * @param settled 입력이 멈춘 뒤의 값(디바운스). 이 값으로 형식을 검사하고 서버에 중복 여부를 묻습니다.
 * @param available 서버 응답. undefined는 응답 전, null은 요청 실패
 */
export function nicknameStatus(nickname: string, settled: string, available: boolean | null | undefined): NicknameStatus {
  if (!nickname.trim()) return "idle";
  if (nickname !== settled) return "checking";
  if (nicknameError(nickname)) return "invalid";
  if (available === undefined) return "checking";
  if (available === null) return "error";
  return available ? "available" : "taken";
}

export type AgreementKey = "terms" | "privacy" | "marketing";
export type Agreements = Record<AgreementKey, boolean>;

export const AGREEMENT_ITEMS: { key: AgreementKey; label: string; required: boolean; termsType: "service" | "privacy" | "marketing" }[] = [
  { key: "terms", label: "서비스 이용약관 동의", required: true, termsType: "service" },
  { key: "privacy", label: "개인정보 수집 및 이용 동의", required: true, termsType: "privacy" },
  { key: "marketing", label: "마케팅 정보 수신 동의", required: false, termsType: "marketing" },
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
