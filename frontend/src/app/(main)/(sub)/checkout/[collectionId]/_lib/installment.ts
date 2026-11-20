/** 카드 할부: 5만 원 이상부터 2 · 3 · 6개월 (0 = 일시불) */
export const INSTALLMENT_MIN_AMOUNT = 50_000;

export function installmentOptions(amount: number): number[] {
  return amount >= INSTALLMENT_MIN_AMOUNT ? [0, 2, 3, 6] : [0];
}

export const installmentLabel = (months: number) => (months === 0 ? "일시불" : `${months}개월`);
