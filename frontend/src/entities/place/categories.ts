import type { PlaceCategory } from "./model";

export const CATEGORY_LABEL: Record<PlaceCategory, string> = {
  CAFE: "카페",
  RESTAURANT: "식당",
  PHOTO_SPOT: "사진 명소",
  SHOP: "소품샵",
  EXHIBITION: "전시",
  STREET: "거리",
  PARK: "공원·산책",
  ATTRACTION: "명소",
  STATION: "역",
  ETC: "기타",
};

/** 내 지도 카테고리 필터에 노출할 순서 */
export const FILTER_CATEGORIES: PlaceCategory[] = ["CAFE", "RESTAURANT", "SHOP", "PARK", "PHOTO_SPOT", "EXHIBITION", "STREET"];

export const categoryLabel = (c: PlaceCategory) => CATEGORY_LABEL[c] ?? "기타";
