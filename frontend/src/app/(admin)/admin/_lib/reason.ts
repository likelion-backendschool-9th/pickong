/** Java·PostgreSQL과 동일하게 Unicode 공백을 양끝에서 제거하고 코드 포인트를 셉니다. */
export const normalizeReason = (value: string) => value.replace(/^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]+$/gu, "");

export function reasonError(value: string, required: boolean): string | null {
  const reason = normalizeReason(value);
  if (required && !reason) return "처리 사유를 입력해 주세요.";
  if (Array.from(reason).length > 200) return "사유는 200자까지 입력할 수 있어요.";
  return null;
}
