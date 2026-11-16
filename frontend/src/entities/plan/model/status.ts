import type { PlanStatus } from "./types";

export const PLAN_STATUS_LABEL: Record<PlanStatus, string> = {
  PLANNING: "계획 중",
  UPCOMING: "계획 완료",
  DONE: "다녀옴",
};
