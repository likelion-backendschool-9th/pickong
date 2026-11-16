import { formatShortDate } from "@/shared/lib/date";
import type { Plan } from "./types";

/**
 * 일정 공유 문구: 일정은 본인만 열 수 있어서 링크 대신 내용을 글로 보냅니다.
 * 예) 성수에서 보내는 토요일 · 11.21 (토) · 성수동
 *     1. 베이커리 소금
 *     2. 식당 A (12:30)
 */
export function planShareText(plan: Plan): string {
  const head = `${plan.title} · ${formatShortDate(plan.date)}${plan.region ? ` · ${plan.region}` : ""}`;
  const lines = plan.items.map((it, i) => `${i + 1}. ${it.place.name}${it.fixedTime ? ` (${it.fixedTime})` : ""}`);
  return [head, ...lines].join("\n");
}
