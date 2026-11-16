import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Plan, PlanItem, PlanStatus } from "../model/types";
import { planApi, type RecordVisitsInput } from "./planApi";

export const planKeys = {
  all: ["plans"] as const,
  list: () => [...planKeys.all, "list"] as const,
  detail: (id: string) => [...planKeys.all, "detail", id] as const,
};

export const usePlans = () => useQuery({ queryKey: planKeys.list(), queryFn: planApi.list });
export const usePlan = (id: string) => useQuery({ queryKey: planKeys.detail(id), queryFn: () => planApi.get(id) });

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

export function useDeletePlan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: planApi.remove,
    onSuccess: (_r, id) => {
      qc.removeQueries({ queryKey: planKeys.detail(id) });
      return qc.invalidateQueries({ queryKey: planKeys.list() });
    },
  });
}

/** 계획 완료 · 다시 수정 */
export function useSetPlanStatus(planId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (status: Exclude<PlanStatus, "DONE">) => planApi.setStatus(planId, status),
    onSuccess: (plan) => {
      qc.setQueryData(planKeys.detail(planId), plan);
      return qc.invalidateQueries({ queryKey: planKeys.list() });
    },
  });
}

export function useRecordVisits(planId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordVisitsInput) => planApi.recordVisits(planId, input),
    onSuccess: (plan) => {
      qc.setQueryData(planKeys.detail(planId), plan);
      return Promise.all([
        qc.invalidateQueries({ queryKey: planKeys.all }),
        qc.invalidateQueries({ queryKey: ["saved-places"] }),
      ]);
    },
  });
}
