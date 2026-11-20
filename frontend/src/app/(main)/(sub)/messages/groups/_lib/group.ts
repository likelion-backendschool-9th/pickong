/** 그룹 채팅 규칙: 이름 20자, 나를 빼고 2명 이상 · 전체 30명까지 */
export const GROUP_NAME_MAX = 20;
export const GROUP_MIN_INVITES = 2;
export const GROUP_MAX_MEMBERS = 30;

export function groupNameError(name: string): string | null {
  const v = name.trim();
  if (!v) return "채팅방 이름을 입력해 주세요.";
  return v.length > GROUP_NAME_MAX ? `채팅방 이름은 ${GROUP_NAME_MAX}자까지 쓸 수 있어요.` : null;
}

export function toggleId(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
}

/** 지금 인원(나 포함)에 더 초대할 수 있는 사람 수 */
export const remainingSeats = (memberCount: number) => Math.max(0, GROUP_MAX_MEMBERS - memberCount);
