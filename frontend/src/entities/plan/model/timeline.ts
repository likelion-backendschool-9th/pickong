import { openStateAt } from "@/entities/place";
import { weekday } from "@/shared/lib/date";
import { estimateTravelMinutes } from "@/shared/lib/geo";
import { toMinutes } from "@/shared/lib/time";
import type { Leg, Plan, PlanItem, PlanWarning, Timeline, TimelineEntry } from "./types";

/** 이전 지점 → index번째 장소 */
export function legEndpoints(plan: Pick<Plan, "startPlace">, items: PlanItem[], index: number) {
  return { from: index === 0 ? plan.startPlace : items[index - 1].place, to: items[index].place };
}

export function estimateLeg(plan: Pick<Plan, "startPlace">, items: PlanItem[], index: number): Leg {
  const { from, to } = legEndpoints(plan, items, index);
  return { minutes: estimateTravelMinutes(from.location, to.location), isEstimate: true };
}

/**
 * 하루 시간표를 계산합니다. 저장하지 않고 순서·체류 시간·이동 시간이 바뀔 때마다 다시 계산합니다.
 *
 * 규칙
 * - 확정 예약은 예약 시간 그대로 둡니다. 늦게 도착해도 시작·종료 시간을 바꾸지 않고 경고만 냅니다.
 * - 자유 일정은 도착 즉시 시작하고, 오픈 전에 도착하면 오픈 시각까지 기다립니다.
 * - legs[i]가 없으면 직선거리 추정치를 사용합니다.
 */
export function buildTimeline(
  plan: Pick<Plan, "date" | "startTime" | "endTime" | "startPlace">,
  items: PlanItem[],
  legs: (Leg | undefined)[] = [],
): Timeline {
  const day = weekday(plan.date);
  const startAt = toMinutes(plan.startTime);
  let cursor = startAt;
  const entries: TimelineEntry[] = [];

  items.forEach((item, i) => {
    const leg = legs[i] ?? estimateLeg(plan, items, i);
    const arriveAt = cursor + leg.minutes;
    const warnings: PlanWarning[] = [];
    let startVisit = arriveAt;
    let departAt: number;
    let hoursUnknown = false;

    if (item.kind === "RESERVATION" && item.reservation) {
      const resStart = toMinutes(item.reservation.startTime);
      const resEnd = toMinutes(item.reservation.endTime);
      if (arriveAt > resStart) {
        warnings.push({
          type: "LATE_FOR_RESERVATION",
          arriveAt,
          startAt: resStart,
          lateMinutes: arriveAt - resStart,
          causeItemId: i > 0 ? items[i - 1].id : null,
        });
      }
      startVisit = resStart;
      departAt = Math.max(resEnd, arriveAt);
    } else {
      const state = openStateAt(item.place.businessHours, day, arriveAt);
      if (state.type === "UNKNOWN") hoursUnknown = true;
      if (state.type === "CLOSED_DAY") warnings.push({ type: "CLOSED_DAY" });
      if (state.type === "BEFORE_OPEN") startVisit = state.openAt;
      if (state.type === "AFTER_CLOSE") {
        warnings.push({ type: "AFTER_CLOSE", arriveAt, closeAt: state.closeAt, lateMinutes: arriveAt - state.closeAt });
      }
      departAt = startVisit + item.stayMinutes;
      if ((state.type === "OPEN" || state.type === "BEFORE_OPEN") && item.place.businessHours) {
        const closeAt = toMinutes(item.place.businessHours.close);
        if (departAt > closeAt) warnings.push({ type: "CLOSES_DURING_STAY", closeAt, overMinutes: departAt - closeAt });
      }
    }

    entries.push({
      itemId: item.id,
      arriveAt,
      startAt: startVisit,
      departAt,
      waitMinutes: Math.max(0, startVisit - arriveAt),
      leg,
      warnings,
      hoursUnknown,
    });
    cursor = departAt;
  });

  const endAt = cursor;
  return {
    entries,
    startAt,
    endAt,
    overEndMinutes: Math.max(0, endAt - toMinutes(plan.endTime)),
    warningCount: entries.reduce((n, e) => n + e.warnings.length, 0),
  };
}
