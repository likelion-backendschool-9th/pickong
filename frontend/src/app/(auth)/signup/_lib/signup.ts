/** 회원가입 입력 검증과 약관 동의 상태. 오류가 없으면 null을 돌려줍니다. */

export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 10;

export function nicknameError(nickname: string): string | null {
  const length = [...nickname.trim()].length;
  if (length === 0) return "닉네임을 입력해 주세요.";
  if (length < NICKNAME_MIN || length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`;
  return null;
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
