import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { adminPlaceApi } from "./adminPlaceApi";
import type { AdminPlaceCreateInput, AdminPlaceDetail, AdminPlaceSummary, AdminPlaceUpdateInput } from "../_lib/models";

/** 회원 placeKeys·관리자 제보 키와 겹치지 않습니다. ["admin"] 전체 무효화에는 포함됩니다. */
export const adminPlaceKeys = {
  all: ["admin", "places"] as const,
  list: () => [...adminPlaceKeys.all, "list"] as const,
  detail: (id: string) => [...adminPlaceKeys.all, "detail", id] as const,
};

export const useAdminPlaces = () => useQuery({ queryKey: adminPlaceKeys.list(), queryFn: () => adminPlaceApi.list() });

export function useAdminPlace(placeId: string, enabled = true) {
  return useQuery({
    queryKey: adminPlaceKeys.detail(placeId),
    queryFn: ({ signal }) => adminPlaceApi.detail(placeId, signal),
    enabled: enabled && placeId.length > 0,
  });
}

export function useCreateAdminPlace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: AdminPlaceCreateInput) => adminPlaceApi.create(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: adminPlaceKeys.list() }),
  });
}

export function useUpdateAdminPlace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: AdminPlaceUpdateInput & { id: string }) => adminPlaceApi.update(id, input),
    onSuccess: (place, variables) => {
      qc.setQueryData<AdminPlaceDetail>(adminPlaceKeys.detail(variables.id), place);
      return qc.invalidateQueries({ queryKey: adminPlaceKeys.list() });
    },
  });
}

export function useDeleteAdminPlace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => adminPlaceApi.remove(id),
    onSuccess: async (_result, id) => {
      await qc.cancelQueries({ queryKey: adminPlaceKeys.detail(id) });
      qc.removeQueries({ queryKey: adminPlaceKeys.detail(id) });
      qc.setQueryData<AdminPlaceSummary[]>(adminPlaceKeys.list(), (current) => {
        if (!Array.isArray(current)) return current;
        return current.filter((place) => !place || typeof place !== "object" || place.id !== id);
      });
      return qc.invalidateQueries({ queryKey: adminPlaceKeys.list() });
    },
  });
}
