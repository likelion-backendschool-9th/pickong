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

/** 내 지도 카테고리 필터 기본값 (사용자가 칩을 편집하지 않았을 때의 보이는 칩과 순서) */
export const FILTER_CATEGORIES: PlaceCategory[] = ["CAFE", "RESTAURANT", "SHOP", "PARK", "PHOTO_SPOT", "EXHIBITION", "STREET"];

/** 칩 편집에서 고를 수 있는 전체 카테고리 (장소의 종류 자체는 사용자가 바꾸지 않습니다) */
export const ALL_CATEGORIES = Object.keys(CATEGORY_LABEL) as PlaceCategory[];

export const categoryLabel = (c: PlaceCategory) => CATEGORY_LABEL[c] ?? "기타";
