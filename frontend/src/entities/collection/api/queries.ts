import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { collectionApi, type CollectionInput, type PaymentMethod } from "./collectionApi";

export const collectionKeys = {
  all: ["collections"] as const,
  mine: () => [...collectionKeys.all, "mine"] as const,
  byUser: (username: string) => [...collectionKeys.all, "user", username] as const,
  detail: (id: string) => [...collectionKeys.all, "detail", id] as const,
};

export const useMyCollections = () => useQuery({ queryKey: collectionKeys.mine(), queryFn: collectionApi.mine });
export const useUserCollections = (username: string) =>
  useQuery({ queryKey: collectionKeys.byUser(username), queryFn: () => collectionApi.byUser(username) });
export const useCollection = (id: string) =>
  useQuery({ queryKey: collectionKeys.detail(id), queryFn: () => collectionApi.get(id) });

export function useCreateCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CollectionInput) => collectionApi.create(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: collectionKeys.all }),
  });
}

export function useUpdateCollection(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<CollectionInput> & { removePlaceIds?: string[] }) => collectionApi.update(id, input),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: collectionKeys.all }),
        qc.invalidateQueries({ queryKey: ["saved-places"] }),
      ]),
  });
}

/** 컬렉션만 삭제합니다. 저장한 장소는 내 지도에 남습니다. */
export function useDeleteCollection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => collectionApi.remove(id),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: collectionKeys.all }),
        qc.invalidateQueries({ queryKey: ["saved-places"] }),
      ]),
  });
}

/** 유료 컬렉션 구매: 성공하면 전체 장소가 담긴 상세로 바로 바꾸고 목록의 잠금 표시도 갱신합니다. */
export function usePurchaseCollection(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { method: PaymentMethod; amount: number }) => collectionApi.purchase(id, input),
    onSuccess: (detail) => {
      qc.setQueryData(collectionKeys.detail(id), detail);
      return qc.invalidateQueries({ queryKey: collectionKeys.all, predicate: (q) => q.queryKey[2] !== id });
    },
  });
}
