import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";
import type { PinColor } from "../model";
import type { CollectionDetailDto, CollectionDto } from "./dto";
import { toCollection, toCollectionDetail } from "./mapper";

export interface CollectionInput {
  title: string;
  description: string;
  visibility: Visibility;
  pinColor: PinColor;
}

export const collectionApi = {
  mine: async () => (await http.get<CollectionDto[]>("/collections")).map(toCollection),
  byUser: async (username: string) => (await http.get<CollectionDto[]>(`/users/${username}/collections`)).map(toCollection),
  get: async (id: string) => toCollectionDetail(await http.get<CollectionDetailDto>(`/collections/${id}`)),
  create: async (input: CollectionInput) => toCollection(await http.post<CollectionDto>("/collections", input)),
  update: async (id: string, input: Partial<CollectionInput> & { removePlaceIds?: string[] }) =>
    toCollection(await http.patch<CollectionDto>(`/collections/${id}`, input)),
  remove: (id: string) => http.delete<void>(`/collections/${id}`),
};
