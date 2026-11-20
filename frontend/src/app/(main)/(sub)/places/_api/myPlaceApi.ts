import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { placeKeys, toPlace, type Place, type PlaceCategory, type PlaceDto } from "@/entities/place";
import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";

/* 직접 등록한 장소 관리: 목록 · 수정 · 삭제 */

export interface MyPlace extends Place {
  photoCount: number;
}

export interface UpdatePlaceInput {
  name: string;
  category: PlaceCategory;
  visibility: Visibility;
  description: string;
}

const myPlacesKey = ["me", "places"] as const;

export const useMyPlaces = () =>
  useQuery({
    queryKey: myPlacesKey,
    queryFn: async () =>
      (await http.get<(PlaceDto & { photoCount: number })[]>("/me/places")).map((dto) => ({ ...toPlace(dto), photoCount: dto.photoCount })),
  });

export function useUpdatePlace(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdatePlaceInput) => http.patch<PlaceDto>(`/places/${id}`, input).then(toPlace),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: myPlacesKey });
      qc.invalidateQueries({ queryKey: placeKeys.all });
    },
  });
}

export function useDeletePlace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete<void>(`/places/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: myPlacesKey });
      qc.invalidateQueries({ queryKey: placeKeys.all });
    },
  });
}
