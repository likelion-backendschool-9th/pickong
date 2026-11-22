/** 휴대폰 번호 입력 처리 */

export function digitsOnly(value: string): string {
  return value.replace(/\D/g, "");
}

/** 입력 중인 번호를 010-1234-5678 형태로 맞춥니다. 숫자는 최대 11자리까지 받습니다. */
export function formatPhone(value: string): string {
  const d = digitsOnly(value).slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`;
  // 10자리(011-123-4567)와 11자리(010-1234-5678)는 가운데 자리 수가 다릅니다.
  const mid = d.length === 10 ? 3 : 4;
  return `${d.slice(0, 3)}-${d.slice(3, 3 + mid)}-${d.slice(3 + mid)}`;
}

export function isValidPhone(value: string): boolean {
  return /^01[016789]\d{7,8}$/.test(digitsOnly(value));
}
