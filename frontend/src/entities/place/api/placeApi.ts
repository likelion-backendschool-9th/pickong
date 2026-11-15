import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";
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
  createCustom: async (input: CreateCustomPlaceInput) => toPlace(await http.post<PlaceDto>("/places", input)),
};

export const savedPlaceApi = {
  list: async (): Promise<SavedPlace[]> => (await http.get<SavedPlaceDto[]>("/saved-places")).map(toSavedPlace),
  save: (input: SavePlacesInput) => http.post<SaveResult>("/saved-places", input),
  update: async (input: UpdateSavedPlacesInput) =>
    (await http.patch<SavedPlaceDto[]>("/saved-places", input)).map(toSavedPlace),
  remove: (ids: string[]) => http.delete<void>("/saved-places", { ids }),
};
