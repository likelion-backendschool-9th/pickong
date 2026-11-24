/**
 * 이름(DB display_name). 닉네임(= 사용자 ID)과 별개로, 다른 사람과 같아도 되고 비워 둘 수 있습니다.
 * 비워 두면 화면에는 닉네임이 대신 보입니다. (API의 표시 이름 `nickname` 필드 = 이름 ?? 닉네임)
 * 최초 가입 온보딩과 프로필 편집이 함께 씁니다.
 */

export const NAME_MAX = 20;

/** 앞뒤 공백을 지우고, 비어 있으면 null(이름 없음) */
export function normalizeName(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

/** 비어 있어도 괜찮고, 너무 길 때만 오류 */
export function nameError(value: string): string | null {
  return [...value.trim()].length > NAME_MAX ? `이름은 ${NAME_MAX}자까지 쓸 수 있어요.` : null;
}

/** 소셜 계정의 이름을 기본값으로 씁니다. 길면 최대 길이까지 자릅니다. */
export function suggestedName(socialName: string | null | undefined): string {
  return [...(socialName ?? "").trim()].slice(0, NAME_MAX).join("");
}
