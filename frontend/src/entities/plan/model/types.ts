import type { Place } from "@/entities/place";

/**
 * 일정 상태
 * - PLANNING: 계획 중 (장소 · 순서 · 고정 시간 편집)
 * - UPCOMING: 계획 완료 (보기 전용, 다시 수정 가능)
 * - DONE: 방문 기록까지 마친 지난 일정
 */
export type PlanStatus = "PLANNING" | "UPCOMING" | "DONE";

/**
 * 일정에 넣은 장소 하나.
 * 카페처럼 시간이 정해지지 않은 곳은 fixedTime 없이 순서만 정하고,
 * 영화 · 식당 예약처럼 시간이 정해진 곳만 fixedTime(HH:mm)을 적어 둡니다.
 */
export interface PlanItem {
  id: string;
  place: Place;
  fixedTime: string | null;
  visited: boolean;
}

export interface Plan {
  id: string;
  title: string;
  date: string; // YYYY-MM-DD
  region: string;
  memo: string;
  status: PlanStatus;
  items: PlanItem[];
}
