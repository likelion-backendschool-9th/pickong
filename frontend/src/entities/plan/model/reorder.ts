import { toMinutes } from "@/shared/lib/time";
import type { PlanItem } from "./types";

/** 확정 예약은 드래그로 옮길 수 없습니다. */
export const canDrag = (item: PlanItem) => item.kind === "FREE";

/**
 * 순서 변경. 확정 예약을 옮기려 하면 원래 배열을 그대로 돌려줍니다.
 * 자유 일정은 예약 앞뒤로 자유롭게 옮길 수 있고, 예약 시간 자체는 바뀌지 않습니다.
 */
export function moveItem(items: PlanItem[], fromIndex: number, toIndex: number): PlanItem[] {
  const item = items[fromIndex];
  if (!item || !canDrag(item) || fromIndex === toIndex || toIndex < 0 || toIndex >= items.length) return items;
  const next = items.slice();
  next.splice(fromIndex, 1);
  next.splice(toIndex, 0, item);
  return next;
}

export function removeItem(items: PlanItem[], itemId: string): PlanItem[] {
  return items.filter((i) => i.id !== itemId);
}

export function setStay(items: PlanItem[], itemId: string, stayMinutes: number): PlanItem[] {
  return items.map((i) => (i.id === itemId && i.kind === "FREE" ? { ...i, stayMinutes } : i));
}

export function insertAt(items: PlanItem[], index: number, item: PlanItem): PlanItem[] {
  const next = items.slice();
  next.splice(Math.max(0, Math.min(index, next.length)), 0, item);
  return next;
}

/** 확정 예약을 시간 순서에 맞는 위치에 넣습니다. */
export function insertReservation(items: PlanItem[], item: PlanItem): PlanItem[] {
  if (!item.reservation) return [...items, item];
  const start = toMinutes(item.reservation.startTime);
  const index = items.findIndex((i) => i.reservation && toMinutes(i.reservation.startTime) > start);
  return index === -1 ? [...items, item] : insertAt(items, index, item);
}
