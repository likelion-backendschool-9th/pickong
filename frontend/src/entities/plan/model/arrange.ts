import { insertAt, insertReservation } from "./reorder";
import { buildTimeline } from "./timeline";
import type { Plan, PlanItem } from "./types";

type PlanLike = Pick<Plan, "date" | "startTime" | "endTime" | "startPlace">;

/**
 * 일정 생성 시 초기 배치. 확정 예약을 시간 순으로 두고,
 * 선택한 장소를 선택 순서대로 경고 없이 들어가는 가장 이른 자리에 넣습니다.
 * 들어갈 자리가 없으면 맨 뒤에 붙이고(경고로 표시), 사용자가 직접 조정합니다.
 */
export function arrangeInitial(plan: PlanLike, reservations: PlanItem[], free: PlanItem[]): PlanItem[] {
  let items = reservations.reduce<PlanItem[]>((acc, r) => insertReservation(acc, r), []);
  for (const item of free) {
    let placed: PlanItem[] | null = null;
    for (let pos = 0; pos <= items.length; pos++) {
      const candidate = insertAt(items, pos, item);
      const t = buildTimeline(plan, candidate);
      if (t.warningCount === 0 && t.overEndMinutes === 0) {
        placed = candidate;
        break;
      }
    }
    items = placed ?? [...items, item];
  }
  return items;
}
