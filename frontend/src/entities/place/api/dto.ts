import type { Visibility } from "@/shared/config/visibility";
import type { PlaceCategory, SavedPlaceSourceType, VisitStatus } from "../model";

export interface PlaceDto {
  id: string;
  name: string;
  address: string | null;
  region: string | null;
  lat: number;
  lng: number;
  category: PlaceCategory;
  isCustom: boolean;
  visibility: Visibility;
  description: string | null;
  imageUrl: string | null;
  businessHours: { open: string; close: string; closedDays: number[] } | null;
}

export interface PlaceClusterDto {
  lat: number;
  lng: number;
  count: number;
  bounds: { west: number; south: number; east: number; north: number };
}

export interface PlacesInBoundsDto {
  places: PlaceDto[];
  clusters: PlaceClusterDto[];
}

export interface SavedPlaceDto {
  id: string;
  place: PlaceDto;
  collectionIds: string[];
  visitStatus: VisitStatus;
  memo: string | null;
  savedAt: string;
  source: { type: SavedPlaceSourceType; refId: string | null; label: string };
}

export interface PlaceDetailDto {
  place: PlaceDto;
  saved: SavedPlaceDto | null;
  discoveredFrom: { postId: string; authorUsername: string; quote: string; imageUrl: string | null } | null;
  relatedPostCount: number;
  visits: { planId: string; title: string; date: string }[];
  isOwner?: boolean;
}
