import type { PlanItem, PlanWarning } from "@/entities/plan";
import { formatDuration, toHHmm } from "@/shared/lib/time";

/** 경고를 문제 장소 · 예상 도착 · 영업 종료/예약 시간 · 지연 정도가 드러나는 문구로 바꿉니다. */
export function describeWarning(w: PlanWarning, item: PlanItem, items: PlanItem[]) {
  switch (w.type) {
    case "AFTER_CLOSE":
      return {
        short: `영업 종료 후 도착 · ${toHHmm(w.arriveAt)} 도착`,
        title: "영업시간을 확인해 주세요",
        detail: `${item.place.name}은(는) ${toHHmm(w.closeAt)}에 영업이 끝나요. 지금 순서라면 ${toHHmm(w.arriveAt)}에 도착해 ${formatDuration(w.lateMinutes)} 늦어요.`,
      };
    case "CLOSES_DURING_STAY":
      return {
        short: `머무는 중 영업 종료 · ${formatDuration(w.overMinutes)} 초과`,
        title: "머무는 동안 영업이 끝나요",
        detail: `${item.place.name}은(는) ${toHHmm(w.closeAt)}에 영업이 끝나요. 체류 시간을 ${formatDuration(w.overMinutes)} 줄이거나 순서를 바꿔 보세요.`,
      };
    case "CLOSED_DAY":
      return {
        short: "이날은 휴무일이에요",
        title: "휴무일이에요",
        detail: `${item.place.name}은(는) 일정 날짜에 쉬는 날이에요. 장소를 제외하거나 다른 날로 옮겨 보세요.`,
      };
    case "LATE_FOR_RESERVATION": {
      const cause = items.find((i) => i.id === w.causeItemId);
      return {
        short: `예약에 ${formatDuration(w.lateMinutes)} 늦을 수 있어요`,
        title: `${toHHmm(w.startAt)} ${item.place.name} 예약에 늦을 수 있어요`,
        detail: `${cause ? `${cause.place.name}에서 출발하면 ` : ""}${toHHmm(w.arriveAt)}에 도착해 ${formatDuration(w.lateMinutes)} 지연돼요. 확정 예약 시간은 자동으로 바꾸지 않아요.`,
      };
    }
  }
}

/** 경고 해결 시 손볼 자유 일정: 예약 지연이면 직전 장소, 그 외에는 해당 장소 */
export function targetItemId(w: PlanWarning, item: PlanItem): string | null {
  if (w.type === "LATE_FOR_RESERVATION") return w.causeItemId;
  return item.kind === "FREE" ? item.id : null;
}
