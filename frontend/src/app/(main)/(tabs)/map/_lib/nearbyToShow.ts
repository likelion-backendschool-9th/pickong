import type { Place, PlaceCategory, PlacesInBounds } from "@/entities/place";

export interface NearbyOptions {
  /** ‘저장한 곳만 보기’: 켜면 주변 장소와 묶음을 하나도 그리지 않습니다. */
  savedOnly: boolean;
  /** 저장한 모든 장소 id. 저장 핀과 겹치지 않게 주변 장소에서 뺍니다. */
  savedPlaceIds: Set<string>;
  category: PlaceCategory | "ALL";
  /** 고른 주변 장소. 지도를 옮겨 묶음에 들어가도 점을 유지합니다. */
  picked: Place | null;
  /** 검색에서 고른 장소 */
  focus: Place | null;
}

const NOTHING: PlacesInBounds = { places: [], clusters: [] };

/** 내 지도에 그릴 주변 장소(점)와 묶음(숫자 원). 저장 장소 핀은 따로 그립니다. */
export function nearbyToShow(nearby: PlacesInBounds, { savedOnly, savedPlaceIds, category, picked, focus }: NearbyOptions): PlacesInBounds {
  if (savedOnly) return NOTHING;
  const matches = (p: Place) => !savedPlaceIds.has(p.id) && (category === "ALL" || p.category === category);
  const places = nearby.places.filter(matches);
  const has = (p: Place) => places.some((x) => x.id === p.id);
  if (picked && matches(picked) && !has(picked)) places.push(picked);
  if (focus && !savedPlaceIds.has(focus.id) && !has(focus)) places.push(focus);
  return { places, clusters: nearby.clusters };
}
