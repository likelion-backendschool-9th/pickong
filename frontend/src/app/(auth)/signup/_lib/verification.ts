/** 인증번호(휴대폰 · 이메일 공통) 입력과 유효 시간 표시 */

export const CODE_LENGTH = 6;
/** 인증번호 유효 시간(초) */
export const CODE_TTL_SECONDS = 180;

export function normalizeCode(value: string): string {
  return value.replace(/\D/g, "").slice(0, CODE_LENGTH);
}

/** 남은 초를 mm:ss로 표시합니다. */
export function formatCountdown(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}
