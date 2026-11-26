import type { Visibility } from "@/shared/config/visibility";
import type { LatLng, LatLngBounds } from "@/shared/lib/geo";

export type PlaceCategory =
  | "CAFE"
  | "RESTAURANT"
  | "PHOTO_SPOT"
  | "SHOP"
  | "EXHIBITION"
  | "STREET"
  | "PARK"
  | "ATTRACTION"
  | "STATION"
  | "ETC";

export interface BusinessHours {
  open: string; // HH:mm
  close: string; // HH:mm (24:00 = 자정)
  closedDays: number[]; // 0=일 ~ 6=토
}

export interface Place {
  id: string;
  name: string;
  address: string;
  region: string;
  location: LatLng;
  category: PlaceCategory;
  /** 사용자가 지도에서 좌표를 직접 지정한 장소 */
  isCustom: boolean;
  visibility: Visibility;
  description: string;
  imageUrl: string | null;
  /** 없으면 "영업시간 확인 필요" */
  businessHours: BusinessHours | null;
}

/** 지도에서 하나로 묶은 장소들 */
export interface PlaceCluster {
  id: string;
  /** 묶인 장소들의 평균 위치 */
  location: LatLng;
  count: number;
  /** 묶인 장소들을 모두 포함하는 범위 */
  bounds: LatLngBounds;
}

/** 지도 범위 조회 결과: 혼자 있는 장소와 묶음 */
export interface PlacesInBounds {
  places: Place[];
  clusters: PlaceCluster[];
}

export type VisitStatus = "WANT_TO_GO" | "VISITED";

export type SavedPlaceSourceType = "POST" | "COLLECTION" | "LINK_IMPORT" | "SEARCH" | "CUSTOM";

export interface SavedPlace {
  id: string;
  place: Place;
  collectionIds: string[];
  visitStatus: VisitStatus;
  memo: string;
  savedAt: string;
  source: { type: SavedPlaceSourceType; refId: string | null; label: string };
}

export interface PlaceDetail {
  place: Place;
  saved: SavedPlace | null;
  discoveredFrom: { postId: string; authorUsername: string; quote: string; imageUrl: string | null } | null;
  relatedPostCount: number;
  visits: { planId: string; title: string; date: string }[];
  /** 내가 직접 등록한 장소라 수정 · 삭제할 수 있는지 */
  isOwner: boolean;
}

export interface SaveResult {
  /** 새로 저장한 장소 */
  createdPlaceIds: string[];
  /** 이미 저장되어 있어 컬렉션에만 연결한 장소 */
  linkedPlaceIds: string[];
}
