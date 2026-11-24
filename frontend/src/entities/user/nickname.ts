/**
 * 닉네임 = 사용자 ID 규칙. 최초 가입 온보딩과 프로필 편집이 함께 씁니다.
 * 내 아이디이자 프로필 주소(/users/{닉네임})로 쓰이므로 영문만 쓸 수 있고 중복될 수 없습니다.
 * DB는 users.username(대소문자 무시 UNIQUE)입니다. 이름(display_name)은 따로 정하며 name.ts에 있습니다.
 * 검증 함수는 오류가 없으면 null을 돌려줍니다.
 */

export const NICKNAME_MIN = 3;
export const NICKNAME_MAX = 20;
const NICKNAME_CHARS = /^[a-z0-9._]+$/;

/** 닉네임은 바꾼 뒤 이 기간 동안 다시 바꿀 수 없습니다. 기준은 서버(users.username_changed_at)이고 화면은 안내만 합니다. */
export const NICKNAME_CHANGE_INTERVAL_DAYS = 14;

/**
 * 닉네임 변경 잠금 안내. 아직 바꿀 수 없으면 "12월 8일부터 다시 바꿀 수 있어요." 같은 문구, 바꿀 수 있으면 null.
 * @param changeableAt 서버가 준 다음 변경 가능 시각(ISO). null이면 지금 바꿀 수 있음
 * @param now 지금 시각(ms). 화면에 오래 머물러 그 시각이 지났으면 잠그지 않습니다(서버가 최종 판단)
 */
export function nicknameLockMessage(changeableAt: string | null, now: number = Date.now()): string | null {
  if (!changeableAt) return null;
  const at = new Date(changeableAt);
  if (Number.isNaN(at.getTime()) || at.getTime() <= now) return null;
  return `${at.getMonth() + 1}월 ${at.getDate()}일부터 다시 바꿀 수 있어요.`;
}

/** 입력하는 동안 대문자는 소문자로 바꾸고 공백은 지웁니다. */
export function normalizeNicknameInput(value: string): string {
  return value.toLowerCase().replace(/\s/g, "");
}

export function nicknameError(nickname: string): string | null {
  if (!nickname) return "닉네임을 입력해 주세요.";
  // 한글처럼 쓸 수 없는 글자는 길이보다 먼저 알려 줍니다.
  if (!NICKNAME_CHARS.test(nickname)) return "닉네임은 영문으로만 만들 수 있어요.";
  if (!/^[a-z]/.test(nickname)) return "닉네임은 영문으로 시작해야 해요.";
  if (nickname.length < NICKNAME_MIN || nickname.length > NICKNAME_MAX) {
    return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`;
  }
  if (nickname.endsWith(".") || nickname.includes("..")) return "마침표는 끝에 쓰거나 연달아 쓸 수 없어요.";
  return null;
}

/**
 * 닉네임 입력칸 옆 표시 상태.
 * - idle: 비어 있음 / checking: 입력 중이거나 서버 확인 중 / invalid: 형식 오류
 * - available: 사용 가능(초록 체크) / taken: 이미 사용 중(빨간 X) / error: 확인 요청 실패
 * - current: 지금 내가 쓰고 있는 닉네임 (프로필 편집에서만, 서버에 묻지 않음)
 */
export type NicknameStatus = "idle" | "checking" | "invalid" | "available" | "taken" | "error" | "current";

/**
 * @param nickname 지금 입력값
 * @param settled 입력이 멈춘 뒤의 값(디바운스). 이 값으로 형식을 검사하고 서버에 중복 여부를 묻습니다.
 * @param available 서버 응답. undefined는 응답 전, null은 요청 실패
 * @param current 지금 쓰고 있는 닉네임 (프로필 편집). 같으면 중복 확인 없이 current
 */
export function nicknameStatus(
  nickname: string,
  settled: string,
  available: boolean | null | undefined,
  current?: string,
): NicknameStatus {
  if (!nickname) return "idle";
  if (current !== undefined && nickname === current) return "current";
  if (nickname !== settled) return "checking";
  if (nicknameError(nickname)) return "invalid";
  if (available === undefined) return "checking";
  if (available === null) return "error";
  return available ? "available" : "taken";
}

/** 입력칸 아래 문구: 오류가 있으면 error, 없으면 hint */
export function nicknameFeedback(nickname: string, status: NicknameStatus): { error: string | null; hint: string } {
  const defaultHint = `영문으로 시작하는 ${NICKNAME_MIN}~${NICKNAME_MAX}자 · 숫자 · 밑줄(_) · 마침표(.)`;
  switch (status) {
    case "invalid":
      return { error: nicknameError(nickname), hint: defaultHint };
    case "taken":
      return { error: "이미 사용 중인 닉네임이에요.", hint: defaultHint };
    case "error":
      return { error: "중복 확인을 하지 못했어요. 잠시 후 다시 시도해 주세요.", hint: defaultHint };
    case "checking":
      return { error: null, hint: "확인하고 있어요…" };
    case "available":
      return { error: null, hint: "사용할 수 있는 닉네임이에요." };
    case "current":
      return { error: null, hint: "지금 쓰고 있는 닉네임이에요." };
    default:
      return { error: null, hint: defaultHint };
  }
}
