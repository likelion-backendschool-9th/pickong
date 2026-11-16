import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";
import type { LatLngBounds } from "@/shared/lib/geo";
import type { PlaceCategory, SavedPlace, SavedPlaceSourceType, SaveResult, VisitStatus } from "../model";
import type { PlaceDetailDto, PlaceDto, SavedPlaceDto } from "./dto";
import { toPlace, toPlaceDetail, toSavedPlace } from "./mapper";

export interface CreateCustomPlaceInput {
  name: string;
  lat: number;
  lng: number;
  description: string;
  category: PlaceCategory;
  visibility: Visibility;
  imageUrl: string | null;
}

export interface SavePlacesInput {
  placeIds: string[];
  collectionIds: string[];
  source?: { type: SavedPlaceSourceType; refId: string | null; label: string };
}

export interface UpdateSavedPlacesInput {
  ids: string[];
  visitStatus?: VisitStatus;
  addCollectionIds?: string[];
  removeCollectionIds?: string[];
  setCollectionIds?: string[];
}

export const placeApi = {
  search: async (q: string, signal?: AbortSignal) =>
    (await http.get<PlaceDto[]>("/places/search", { query: { q }, signal })).map(toPlace),
  detail: async (id: string) => toPlaceDetail(await http.get<PlaceDetailDto>(`/places/${id}`)),
  /** 지도 범위 안의 장소 (일반 지도처럼 주변 장소를 깔아 두는 용도) */
  inBounds: async (b: LatLngBounds, signal?: AbortSignal) =>
    (await http.get<PlaceDto[]>("/places", { query: { bbox: [b.west, b.south, b.east, b.north].join(",") }, signal })).map(toPlace),
  createCustom: async (input: CreateCustomPlaceInput) => toPlace(await http.post<PlaceDto>("/places", input)),
};

/** 내 지도 카테고리 칩 설정: 보이는 카테고리를 순서대로 (계정에 저장) */
export const mapCategoryApi = {
  get: async () => (await http.get<{ categories: PlaceCategory[] }>("/me/map-categories")).categories,
  save: async (categories: PlaceCategory[]) =>
    (await http.put<{ categories: PlaceCategory[] }>("/me/map-categories", { categories })).categories,
};

export const savedPlaceApi = {
  list: async (): Promise<SavedPlace[]> => (await http.get<SavedPlaceDto[]>("/saved-places")).map(toSavedPlace),
  save: (input: SavePlacesInput) => http.post<SaveResult>("/saved-places", input),
  update: async (input: UpdateSavedPlacesInput) =>
    (await http.patch<SavedPlaceDto[]>("/saved-places", input)).map(toSavedPlace),
  remove: (ids: string[]) => http.delete<void>("/saved-places", { ids }),
};
