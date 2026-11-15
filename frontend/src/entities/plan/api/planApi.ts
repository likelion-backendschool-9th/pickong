import { http } from "@/shared/api/http";
import type { PlanItem } from "../model/types";
import type { PlanDto, TravelTimeDto, VisitRecordDto } from "./dto";
import { toPlan, toPlanItemInput, toVisitRecord } from "./mapper";

export interface CreatePlanInput {
  title: string;
  date: string;
  region: string;
  startPlaceId: string;
  startTime: string;
  endTime: string;
  memo: string;
  items: PlanItem[];
}

export const planApi = {
  list: async () => (await http.get<PlanDto[]>("/plans")).map(toPlan),
  get: async (id: string) => toPlan(await http.get<PlanDto>(`/plans/${id}`)),
  create: async ({ items, ...rest }: CreatePlanInput) =>
    toPlan(await http.post<PlanDto>("/plans", { ...rest, items: items.map(toPlanItemInput) })),
  updateItems: async (id: string, items: PlanItem[]) =>
    toPlan(await http.put<PlanDto>(`/plans/${id}`, { items: items.map(toPlanItemInput) })),
  recordVisits: async (id: string, visitedItemIds: string[]) =>
    toPlan(await http.post<PlanDto>(`/plans/${id}/visits`, { visitedItemIds })),
  travelTime: (fromId: string, toId: string) =>
    http.get<TravelTimeDto>("/travel-time", { query: { from: fromId, to: toId } }),
  visitsOf: async (username: string) => (await http.get<VisitRecordDto[]>(`/users/${username}/visits`)).map(toVisitRecord),
};
