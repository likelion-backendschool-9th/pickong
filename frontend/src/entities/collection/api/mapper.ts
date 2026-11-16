import { toPlace } from "@/entities/place";
import { toUserSummary } from "@/entities/user";
import type { Collection, CollectionDetail } from "../model";
import type { CollectionDetailDto, CollectionDto } from "./dto";

export const toCollection = (dto: CollectionDto): Collection => ({
  id: dto.id,
  owner: toUserSummary(dto.owner),
  title: dto.title,
  description: dto.description ?? "",
  coverImageUrl: dto.coverImageUrl,
  visibility: dto.visibility,
  pinColor: dto.pinColor,
  placeCount: dto.placeCount,
  isMine: dto.isMine,
  price: dto.price ?? null,
  isPurchased: dto.isPurchased ?? false,
  isLocked: dto.isLocked ?? false,
});

export const toCollectionDetail = (dto: CollectionDetailDto): CollectionDetail => ({
  ...toCollection(dto),
  places: dto.places.map((p) => ({ ...toPlace(p), savedByMe: p.savedByMe })),
  lockedPlaceCount: dto.lockedPlaceCount ?? 0,
});
