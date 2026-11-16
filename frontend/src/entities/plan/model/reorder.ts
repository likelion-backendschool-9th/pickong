import { estimateTravelMinutes } from "@/shared/lib/geo";
import type { PlanItem } from "./types";

/** 순서 변경. 모든 장소를 옮길 수 있습니다 (고정 시간은 순서를 막지 않고 표시만 합니다). */
export function moveItem(items: PlanItem[], fromIndex: number, toIndex: number): PlanItem[] {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= items.length || toIndex >= items.length) return items;
  const next = items.slice();
  const [item] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, item);
  return next;
}

export function removeItem(items: PlanItem[], itemId: string): PlanItem[] {
  return items.filter((i) => i.id !== itemId);
}

/** 고정 시간 설정 · 해제 (null이면 해제) */
export function setFixedTime(items: PlanItem[], itemId: string, fixedTime: string | null): PlanItem[] {
  return items.map((i) => (i.id === itemId ? { ...i, fixedTime } : i));
}

export function appendItem(items: PlanItem[], item: PlanItem): PlanItem[] {
  return [...items, item];
}

/**
 * 고정 시간이 목록 순서와 어긋난 곳: 앞에 있는데 뒤의 고정 시간보다 늦은 경우.
 * 예) 1. 영화관 19:00 → 3. 식당 18:00 이면 { earlier: 영화관, later: 식당 }
 */
export function fixedTimeOrderIssue(items: PlanItem[]): { earlier: PlanItem; later: PlanItem } | null {
  const fixed = items.filter((i) => i.fixedTime);
  for (let a = 0; a < fixed.length; a++) {
    for (let b = a + 1; b < fixed.length; b++) {
      if (fixed[a].fixedTime! > fixed[b].fixedTime!) return { earlier: fixed[a], later: fixed[b] };
    }
  }
  return null;
}

/** 다음 장소까지 이동 시간 어림값(분). 직선거리 기반 참고용입니다. */
export const travelEstimate = (from: PlanItem, to: PlanItem) => estimateTravelMinutes(from.place.location, to.place.location);
