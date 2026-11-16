import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { LatLngBounds } from "@/shared/lib/geo";
import { FILTER_CATEGORIES } from "../categories";
import { mapCategoryApi, placeApi, savedPlaceApi, type SavePlacesInput, type UpdateSavedPlacesInput } from "./placeApi";

export const placeKeys = {
  all: ["places"] as const,
  detail: (id: string) => [...placeKeys.all, "detail", id] as const,
  search: (q: string) => [...placeKeys.all, "search", q] as const,
  inBounds: (key: string) => [...placeKeys.all, "bounds", key] as const,
  saved: () => ["saved-places"] as const,
  mapCategories: () => ["me", "map-categories"] as const,
};

/** 내 지도 카테고리 칩 (불러오기 전에는 기본값) */
export const useMapCategories = () =>
  useQuery({ queryKey: placeKeys.mapCategories(), queryFn: mapCategoryApi.get, placeholderData: FILTER_CATEGORIES });

export function useSaveMapCategories() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: mapCategoryApi.save,
    onSuccess: (categories) => qc.setQueryData(placeKeys.mapCategories(), categories),
  });
}

/** 내 지도·저장 목록·일정 후보가 함께 쓰는 저장 장소 데이터 (지도와 목록은 같은 데이터) */
export const useSavedPlaces = () => useQuery({ queryKey: placeKeys.saved(), queryFn: savedPlaceApi.list });

/**
 * 지도 범위 안의 장소. 범위를 소수 셋째 자리(약 100m)로 묶어 작은 이동에는 다시 요청하지 않고,
 * 새로 불러오는 동안에는 이전 결과를 그대로 보여 줍니다.
 */
export function usePlacesInBounds(bounds: LatLngBounds | null) {
  const round = (n: number) => n.toFixed(3);
  const key = bounds ? [bounds.west, bounds.south, bounds.east, bounds.north].map(round).join(",") : "";
  return useQuery({
    queryKey: placeKeys.inBounds(key),
    queryFn: ({ signal }) => placeApi.inBounds(bounds!, signal),
    enabled: !!bounds,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export const usePlaceDetail = (id: string) =>
  useQuery({ queryKey: placeKeys.detail(id), queryFn: () => placeApi.detail(id) });

export const usePlaceSearch = (q: string) =>
  useQuery({
    queryKey: placeKeys.search(q),
    queryFn: ({ signal }) => placeApi.search(q, signal),
    enabled: q.trim().length > 0,
    staleTime: 60_000,
  });

/** 저장 장소가 바뀌면 영향받는 데이터: 저장 목록, 장소 상세, 컬렉션 장소 수 */
function useInvalidateSaved() {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: placeKeys.saved() }),
      qc.invalidateQueries({ queryKey: placeKeys.all }),
      qc.invalidateQueries({ queryKey: ["collections"] }),
    ]);
}

export function useSavePlaces() {
  const invalidate = useInvalidateSaved();
  return useMutation({ mutationFn: (input: SavePlacesInput) => savedPlaceApi.save(input), onSuccess: invalidate });
}

export function useUpdateSavedPlaces() {
  const invalidate = useInvalidateSaved();
  return useMutation({ mutationFn: (input: UpdateSavedPlacesInput) => savedPlaceApi.update(input), onSuccess: invalidate });
}

export function useDeleteSavedPlaces() {
  const invalidate = useInvalidateSaved();
  return useMutation({ mutationFn: (ids: string[]) => savedPlaceApi.remove(ids), onSuccess: invalidate });
}

export function useCreateCustomPlace() {
  return useMutation({ mutationFn: placeApi.createCustom });
}
