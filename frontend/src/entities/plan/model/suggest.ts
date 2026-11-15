import { openStateAt, type Place } from "@/entities/place";
import { weekday } from "@/shared/lib/date";
import { estimateTravelMinutes } from "@/shared/lib/geo";
import { buildTimeline } from "./timeline";
import type { Plan, PlanItem, Timeline } from "./types";

type PlanLike = Pick<Plan, "date" | "startTime" | "endTime" | "startPlace">;

const MAX_PERMUTE = 7;

function score(t: Timeline, moved: number) {
  const lateness = t.entries.reduce(
    (sum, e) =>
      sum +
      e.warnings.reduce((s, w) => {
        if (w.type === "LATE_FOR_RESERVATION" || w.type === "AFTER_CLOSE") return s + w.lateMinutes;
        if (w.type === "CLOSES_DURING_STAY") return s + w.overMinutes;
        return s + 60;
      }, 0),
    0,
  );
  const travel = t.entries.reduce((s, e) => s + e.leg.minutes, 0);
  // 사용자 순서를 최대한 존중: 문제 해결 정도가 같으면 덜 바꾸는 순서를 고릅니다.
  return [t.warningCount, lateness, t.overEndMinutes, moved, travel] as const;
}

const less = (a: readonly number[], b: readonly number[]) => {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] < b[i];
  return false;
};

function* permutations<T>(arr: T[]): Generator<T[]> {
  if (arr.length <= 1) {
    yield arr.slice();
    return;
  }
  for (let i = 0; i < arr.length; i++) {
    const rest = [...arr.slice(0, i), ...arr.slice(i + 1)];
    for (const p of permutations(rest)) yield [arr[i], ...p];
  }
}

/**
 * 다른 순서 추천. 확정 예약 위치는 그대로 두고 자유 일정의 순서만 바꿔 봅니다.
 * 경고 수 → 지연 시간 → 종료 초과 → 옮긴 장소 수 → 총 이동 시간 순으로 비교하며, 현재보다 나을 때만 제안합니다.
 * 결과는 제안일 뿐이며 사용자가 승인해야 적용됩니다.
 */
export function suggestOrder(plan: PlanLike, items: PlanItem[]): { items: PlanItem[]; timeline: Timeline } | null {
  const freeSlots = items.map((it, i) => (it.kind === "FREE" ? i : -1)).filter((i) => i >= 0);
  const free = freeSlots.map((i) => items[i]);
  if (free.length < 2 || free.length > MAX_PERMUTE) return null;

  const currentTimeline = buildTimeline(plan, items);
  let best = { items, timeline: currentTimeline, score: score(currentTimeline, 0) };

  for (const perm of permutations(free)) {
    const candidate = items.slice();
    freeSlots.forEach((slot, k) => (candidate[slot] = perm[k]));
    const timeline = buildTimeline(plan, candidate);
    const moved = candidate.filter((it, i) => it.id !== items[i].id).length;
    const s = score(timeline, moved);
    if (less(s, best.score)) best = { items: candidate, timeline, score: s };
  }

  return best.items === items ? null : { items: best.items, timeline: best.timeline };
}

export interface GapSuggestion {
  place: Place;
  arriveAt: number;
  travelFromPrev: number;
  travelToNext: number;
}

/**
 * 빈 시간에 들를 수 있는 장소 후보.
 * 이전 장소 → 후보 → 다음 장소 이동 시간, 체류 시간, 영업시간을 함께 고려합니다.
 */
export function suggestForGap(
  candidates: Place[],
  gap: { from: Place; to: Place; startAt: number; endAt: number; date: string; stayMinutes?: number },
): GapSuggestion[] {
  const stay = gap.stayMinutes ?? 40;
  const day = weekday(gap.date);
  return candidates
    .map((place) => {
      const travelFromPrev = estimateTravelMinutes(gap.from.location, place.location);
      const travelToNext = estimateTravelMinutes(place.location, gap.to.location);
      const arriveAt = gap.startAt + travelFromPrev;
      return { place, arriveAt, travelFromPrev, travelToNext };
    })
    .filter(({ place, arriveAt, travelToNext }) => {
      if (arriveAt + stay + travelToNext > gap.endAt) return false;
      const atArrive = openStateAt(place.businessHours, day, arriveAt);
      const atLeave = openStateAt(place.businessHours, day, arriveAt + stay - 1);
      const ok = (s: typeof atArrive) => s.type === "OPEN" || s.type === "UNKNOWN";
      return ok(atArrive) && ok(atLeave);
    })
    .sort((a, b) => a.travelFromPrev + a.travelToNext - (b.travelFromPrev + b.travelToNext));
}
