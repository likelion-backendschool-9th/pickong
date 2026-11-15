import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { collectionApi, type CollectionInput } from "./collectionApi";

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
