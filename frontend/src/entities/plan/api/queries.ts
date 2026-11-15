import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { legEndpoints } from "../model/timeline";
import type { Leg, Plan, PlanItem } from "../model/types";
import { planApi } from "./planApi";

export const planKeys = {
  all: ["plans"] as const,
  list: () => [...planKeys.all, "list"] as const,
  detail: (id: string) => [...planKeys.all, "detail", id] as const,
  visits: (username: string) => [...planKeys.all, "visits", username] as const,
  travel: (fromId: string, toId: string) => ["travel-time", fromId, toId] as const,
};

export const usePlans = () => useQuery({ queryKey: planKeys.list(), queryFn: planApi.list });
export const usePlan = (id: string) => useQuery({ queryKey: planKeys.detail(id), queryFn: () => planApi.get(id) });
export const useVisitRecords = (username: string) =>
  useQuery({ queryKey: planKeys.visits(username), queryFn: () => planApi.visitsOf(username) });

/**
 * 구간별 이동 시간. 같은 구간은 캐시를 재사용하고, 새 구간만 계산을 요청합니다.
 * 계산 중인 구간은 undefined → 시간표에서 추정치(임시값)로 표시됩니다.
 */
export function useTravelLegs(plan: Pick<Plan, "startPlace"> | undefined, items: PlanItem[]): (Leg | undefined)[] {
  const pairs = plan ? items.map((_, i) => legEndpoints(plan, items, i)) : [];
  const results = useQueries({
    queries: pairs.map(({ from, to }) => ({
      queryKey: planKeys.travel(from.id, to.id),
      queryFn: () => planApi.travelTime(from.id, to.id),
      staleTime: Infinity,
    })),
  });
  return results.map((r) => (r.data ? { minutes: r.data.minutes, isEstimate: false, mode: r.data.mode } : undefined));
}

export function useCreatePlan() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: planApi.create, onSuccess: () => qc.invalidateQueries({ queryKey: planKeys.all }) });
}

/** 일정 항목 저장. 화면은 낙관적으로 먼저 바뀌고, 실패하면 이전 상태로 되돌립니다. */
export function useUpdatePlanItems(planId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: PlanItem[]) => planApi.updateItems(planId, items),
    onMutate: async (items) => {
      await qc.cancelQueries({ queryKey: planKeys.detail(planId) });
      const previous = qc.getQueryData<Plan>(planKeys.detail(planId));
      if (previous) qc.setQueryData<Plan>(planKeys.detail(planId), { ...previous, items });
      return { previous };
    },
    onError: (_e, _items, ctx) => {
      if (ctx?.previous) qc.setQueryData(planKeys.detail(planId), ctx.previous);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: planKeys.list() }),
  });
}

export function useRecordVisits(planId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (visitedItemIds: string[]) => planApi.recordVisits(planId, visitedItemIds),
    onSuccess: (plan) => {
      qc.setQueryData(planKeys.detail(planId), plan);
      return Promise.all([
        qc.invalidateQueries({ queryKey: planKeys.all }),
        qc.invalidateQueries({ queryKey: ["saved-places"] }),
      ]);
    },
  });
}
