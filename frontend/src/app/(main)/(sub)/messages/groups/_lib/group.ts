import { GROUP_MAX_MEMBERS } from "@/entities/conversation";

/*
 * 그룹 채팅 화면 규칙 (백엔드와 같음): 방장 없음, 나 포함 3~10명, 초대는 아이디로.
 * 이름 길이 · 인원 숫자는 @/entities/conversation의 상수를 씁니다.
 */

/** 초대할 때 고른 사람 (아이디로 찾은 회원) */
export interface PickedUser {
  username: string;
  profileImageUrl: string | null;
}

/** 입력한 아이디를 정리합니다: 앞뒤 공백 · 맨 앞 @ 제거, 소문자 (아이디는 대소문자를 구분하지 않음) */
export const normalizeUsername = (raw: string) => raw.trim().replace(/^@/, "").toLowerCase();

/**
 * 아이디를 추가하기 전에 확인합니다. 문제가 없으면 null.
 *
 * @param taken 이미 고른 사람 · 이미 참여 중인 사람 · 나의 아이디 (소문자)
 * @param seats 더 고를 수 있는 사람 수
 */
export function pickError(username: string, taken: string[], seats: number): string | null {
  if (!username) return "아이디를 입력해 주세요.";
  if (taken.includes(username)) return "이미 추가한 사람이에요.";
  if (seats <= 0) return `그룹 채팅은 ${GROUP_MAX_MEMBERS}명까지 참여할 수 있어요.`;
  return null;
}

/** 지금 인원(나 포함)에 더 초대할 수 있는 사람 수 */
export const remainingSeats = (memberCount: number) => Math.max(0, GROUP_MAX_MEMBERS - memberCount);
