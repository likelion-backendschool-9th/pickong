import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { LatLngBounds } from "@/shared/lib/geo";
import { isPlaceBoundsQueryAllowed, placeBoundsCacheKey, quantizePlaceBounds } from "../boundsQuery";
import { FILTER_CATEGORIES } from "../categories";
import type { PlaceCategory, PlaceCluster, PlacesInBounds } from "../model";
import { mapCategoryApi, placeApi, savedPlaceApi, type SavePlacesInput, type UpdateSavedPlacesInput } from "./placeApi";

export const placeKeys = {
  all: ["places"] as const,
  detail: (id: string) => [...placeKeys.all, "detail", id] as const,
  search: (q: string) => [...placeKeys.all, "search", q] as const,
  inBounds: (key: string, category: PlaceCategory | null) => [...placeKeys.all, "bounds", key, category] as const,
  inCluster: (id: string, category: PlaceCategory | null) => [...placeKeys.all, "cluster", id, category] as const,
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

const NO_PLACES: PlacesInBounds = { places: [], clusters: [] };
const categoryFilter = (category: PlaceCategory | "ALL") => (category === "ALL" ? null : category);

/**
 * 지도 범위 안의 장소와 묶음.
 * - 소수 셋째 자리(~100m)로 넓혀 동일·근접 영역은 캐시·중복 요청을 막습니다.
 * - AbortSignal로 이전 요청을 취소해, 빠른 이동 시 늦은 응답이 최신 목록을 덮지 않습니다.
 * - 가로·세로 15km를 넘는 광역은 조회하지 않습니다.
 * - 새 범위를 불러오는 동안에는 이전 결과를 잠시 유지합니다.
 */
export function usePlacesInBounds(bounds: LatLngBounds | null, category: PlaceCategory | "ALL" = "ALL") {
  const allowed = !!bounds && isPlaceBoundsQueryAllowed(bounds);
  const quantized = allowed && bounds ? quantizePlaceBounds(bounds) : null;
  const key = quantized ? placeBoundsCacheKey(quantized) : "";
  const filter = categoryFilter(category);
  const result = useQuery({
    queryKey: placeKeys.inBounds(key || "none", filter),
    queryFn: ({ signal }) => placeApi.inBounds(quantized!, filter, signal),
    enabled: !!quantized,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
  // 광역·미준비 구간에서는 이전 동네 핀을 그대로 두지 않습니다.
  if (!quantized) {
    return { ...result, data: NO_PLACES };
  }
  return { ...result, data: result.data ?? NO_PLACES };
}

/** 한 묶음에 든 장소 목록. 묶음을 열었을 때만 조회합니다. */
export function usePlacesInCluster(cluster: PlaceCluster | null, category: PlaceCategory | "ALL" = "ALL") {
  const filter = categoryFilter(category);
  return useQuery({
    queryKey: placeKeys.inCluster(cluster?.id ?? "none", filter),
    queryFn: ({ signal }) => placeApi.listInBounds(cluster!.bounds, filter, signal),
    enabled: !!cluster,
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
