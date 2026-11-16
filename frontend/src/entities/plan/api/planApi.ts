import { http } from "@/shared/api/http";
import type { PlanItem, PlanStatus } from "../model/types";
import type { PlanDto } from "./dto";
import { toPlan, toPlanItemInput } from "./mapper";

export interface CreatePlanInput {
  title: string;
  date: string;
  region: string;
  memo: string;
  items: PlanItem[];
}

export interface RecordVisitsInput {
  /** 일정에 있던 곳 중 다녀온 곳 */
  visitedItemIds: string[];
  /** 일정에 없었지만 다녀온 곳 (일정 끝에 추가되고 방문 완료로 기록) */
  extraPlaceIds: string[];
}

export const planApi = {
  list: async () => (await http.get<PlanDto[]>("/plans")).map(toPlan),
  get: async (id: string) => toPlan(await http.get<PlanDto>(`/plans/${id}`)),
  /** 새 일정은 ‘계획 중’으로 만들어집니다. */
  create: async ({ items, ...rest }: CreatePlanInput) =>
    toPlan(await http.post<PlanDto>("/plans", { ...rest, items: items.map(toPlanItemInput) })),
  updateItems: async (id: string, items: PlanItem[]) =>
    toPlan(await http.put<PlanDto>(`/plans/${id}`, { items: items.map(toPlanItemInput) })),
  /** 계획 완료(UPCOMING) · 다시 수정(PLANNING) */
  setStatus: async (id: string, status: Exclude<PlanStatus, "DONE">) =>
    toPlan(await http.put<PlanDto>(`/plans/${id}`, { status })),
  remove: (id: string) => http.delete<void>(`/plans/${id}`),
  recordVisits: async (id: string, input: RecordVisitsInput) => toPlan(await http.post<PlanDto>(`/plans/${id}/visits`, input)),
};
