/** 크리에이터 신청 · 정산 계좌 입력 검증 (오류가 없으면 null) */

export const CREATOR_CATEGORIES = [
  { value: "CAFE", label: "카페" },
  { value: "FOOD", label: "맛집" },
  { value: "TRAVEL", label: "여행 · 나들이" },
  { value: "CULTURE", label: "전시 · 문화" },
  { value: "ETC", label: "기타" },
] as const;

export const BANKS = ["국민은행", "신한은행", "우리은행", "하나은행", "농협은행", "기업은행", "카카오뱅크", "토스뱅크", "케이뱅크"];

export const INTRO_MIN = 10;

export function introError(intro: string): string | null {
  return intro.trim().length < INTRO_MIN ? `소개를 ${INTRO_MIN}자 이상 써 주세요.` : null;
}

/** 채널 주소는 선택. 쓰면 http(s) 주소여야 합니다. */
export function channelUrlError(url: string): string | null {
  const v = url.trim();
  if (!v) return null;
  return /^https?:\/\/\S+\.\S+/.test(v) ? null : "https://로 시작하는 주소를 입력해 주세요.";
}

export const accountDigits = (value: string) => value.replace(/\D/g, "").slice(0, 14);

export function accountNumberError(value: string): string | null {
  const n = accountDigits(value).length;
  if (n === 0) return "계좌번호를 입력해 주세요.";
  return n < 10 ? "계좌번호는 숫자 10~14자리예요." : null;
}

/** 수수료를 뗀 정산 예정 금액 */
export const netAmount = (amount: number, feeRate: number) => amount - Math.round(amount * feeRate);
