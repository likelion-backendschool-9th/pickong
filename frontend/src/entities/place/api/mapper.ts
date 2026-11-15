import type { Place, PlaceDetail, SavedPlace } from "../model";
import type { PlaceDetailDto, PlaceDto, SavedPlaceDto } from "./dto";

export const toPlace = (dto: PlaceDto): Place => ({
  id: dto.id,
  name: dto.name,
  address: dto.address ?? "",
  region: dto.region ?? "",
  location: { lat: dto.lat, lng: dto.lng },
  category: dto.category,
  isCustom: dto.isCustom,
  visibility: dto.visibility,
  description: dto.description ?? "",
  imageUrl: dto.imageUrl,
  businessHours: dto.businessHours,
});

export const toSavedPlace = (dto: SavedPlaceDto): SavedPlace => ({
  id: dto.id,
  place: toPlace(dto.place),
  collectionIds: dto.collectionIds,
  visitStatus: dto.visitStatus,
  memo: dto.memo ?? "",
  savedAt: dto.savedAt,
  source: dto.source,
});

export const toPlaceDetail = (dto: PlaceDetailDto): PlaceDetail => ({
  place: toPlace(dto.place),
  saved: dto.saved ? toSavedPlace(dto.saved) : null,
  discoveredFrom: dto.discoveredFrom,
  relatedPostCount: dto.relatedPostCount,
  visits: dto.visits,
});
