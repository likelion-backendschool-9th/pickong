import { toMinutes } from "@/shared/lib/time";
import type { BusinessHours } from "./model";

export type OpenState =
  | { type: "OPEN"; closeAt: number }
  | { type: "BEFORE_OPEN"; openAt: number }
  | { type: "AFTER_CLOSE"; closeAt: number }
  | { type: "CLOSED_DAY" }
  | { type: "UNKNOWN" };

/** 해당 요일·시각(분)에 영업 중인지 판정합니다. 영업시간 정보가 없으면 UNKNOWN */
export function openStateAt(hours: BusinessHours | null, day: number, minutes: number): OpenState {
  if (!hours) return { type: "UNKNOWN" };
  if (hours.closedDays.includes(day)) return { type: "CLOSED_DAY" };
  const openAt = toMinutes(hours.open);
  const closeAt = toMinutes(hours.close);
  if (minutes < openAt) return { type: "BEFORE_OPEN", openAt };
  if (minutes >= closeAt) return { type: "AFTER_CLOSE", closeAt };
  return { type: "OPEN", closeAt };
}

export function formatHours(hours: BusinessHours | null): string {
  if (!hours) return "영업시간 확인 필요";
  if (hours.open === "00:00" && hours.close === "24:00") return "24시간";
  return `${hours.open} – ${hours.close}`;
}
