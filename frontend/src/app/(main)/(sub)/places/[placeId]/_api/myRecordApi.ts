import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

/* 저장한 장소에 남기는 나만의 기록: 사진 · 태그 (나만 볼 수 있어요) */

export interface PlacePhoto {
  id: string;
  url: string;
  createdAt: string;
}

const photosKey = (placeId: string) => ["me", "places", placeId, "photos"] as const;
const tagsKey = (placeId: string) => ["me", "places", placeId, "tags"] as const;

export const usePlacePhotos = (placeId: string) =>
  useQuery({ queryKey: photosKey(placeId), queryFn: () => http.get<PlacePhoto[]>(`/me/places/${placeId}/photos`) });

export function useAddPlacePhotos(placeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (urls: string[]) => http.post<PlacePhoto[]>(`/me/places/${placeId}/photos`, { urls }),
    onSuccess: () => qc.invalidateQueries({ queryKey: photosKey(placeId) }),
  });
}

export function useDeletePlacePhotos(placeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (photoIds: string[]) => http.delete<void>(`/me/places/${placeId}/photos`, { photoIds }),
    onSuccess: () => qc.invalidateQueries({ queryKey: photosKey(placeId) }),
  });
}

export const usePlaceTags = (placeId: string) =>
  useQuery({ queryKey: tagsKey(placeId), queryFn: () => http.get<string[]>(`/me/places/${placeId}/tags`) });

export function useSavePlaceTags(placeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tags: string[]) => http.put<string[]>(`/me/places/${placeId}/tags`, { tags }),
    onSuccess: (tags) => {
      qc.setQueryData(tagsKey(placeId), tags);
      qc.invalidateQueries({ queryKey: ["me", "tags"] });
    },
  });
}
