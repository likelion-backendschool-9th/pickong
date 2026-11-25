/**
 * 탈퇴하기 버튼 상태.
 * - 사유가 없거나 '기타'인데 내용이 비어 있으면 버튼을 끕니다.
 * - 사유를 골랐으면 버튼을 켜 둡니다. 확인 체크가 빠진 채 누르면 체크 칸을 흔들어 알려 줍니다(누르기 전에는 막지 않음).
 */
export type WithdrawAction = "DISABLED" | "NEEDS_CONFIRM" | "READY";

export function withdrawAction(reason: string, detail: string, confirmed: boolean): WithdrawAction {
  if (!reason || (reason === "ETC" && !detail.trim())) return "DISABLED";
  return confirmed ? "READY" : "NEEDS_CONFIRM";
}
