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
  /** 유료 가격(원). null이면 무료. 크리에이터의 전체 공개 컬렉션만 */
  price?: number | null;
}

/** 결제 수단: 지금은 테스트 결제만. PG 연결 후 결제창 승인 정보가 추가됩니다. */
export type PaymentMethod = "TEST";

export const collectionApi = {
  mine: async () => (await http.get<CollectionDto[]>("/collections")).map(toCollection),
  byUser: async (username: string) => (await http.get<CollectionDto[]>(`/users/${username}/collections`)).map(toCollection),
  get: async (id: string) => toCollectionDetail(await http.get<CollectionDetailDto>(`/collections/${id}`)),
  create: async (input: CollectionInput) => toCollection(await http.post<CollectionDto>("/collections", input)),
  update: async (id: string, input: Partial<CollectionInput> & { removePlaceIds?: string[] }) =>
    toCollection(await http.patch<CollectionDto>(`/collections/${id}`, input)),
  remove: (id: string) => http.delete<void>(`/collections/${id}`),
  /** 유료 컬렉션 단건 구매. amount는 화면에 보여 준 가격 (서버 가격과 다르면 거절) */
  purchase: async (id: string, input: { method: PaymentMethod; amount: number }) =>
    toCollectionDetail(await http.post<CollectionDetailDto>(`/collections/${id}/purchase`, input)),
};
