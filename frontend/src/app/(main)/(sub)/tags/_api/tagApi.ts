import { useQuery } from "@tanstack/react-query";
import { toPlace, type Place, type PlaceDto } from "@/entities/place";
import { http } from "@/shared/api/http";

/* 개인 태그: 내가 저장한 장소에 단 태그로 찾기 */

export interface TagSummary {
  name: string;
  placeCount: number;
}

export interface TaggedPlace {
  savedPlaceId: string;
  place: Place;
  tags: string[];
  memo: string;
}

interface TaggedPlaceDto {
  id: string;
  place: PlaceDto;
  memo: string | null;
  tags: string[];
}

export const useMyTags = (q: string) =>
  useQuery({
    queryKey: ["me", "tags", q],
    queryFn: () => http.get<TagSummary[]>("/me/tags", { query: { q } }),
    placeholderData: (prev) => prev,
  });

export const useTagPlaces = (name: string) =>
  useQuery({
    queryKey: ["me", "tags", "places", name],
    queryFn: async () => {
      const res = await http.get<{ name: string; places: TaggedPlaceDto[] }>(`/me/tags/${encodeURIComponent(name)}/places`);
      return res.places.map((s): TaggedPlace => ({ savedPlaceId: s.id, place: toPlace(s.place), tags: s.tags, memo: s.memo ?? "" }));
    },
  });
