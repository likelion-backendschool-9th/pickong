import { distanceMeters, type LatLngBounds } from "@/shared/lib/geo";

/** 소수 셋째 자리 ≈ 100m. 작은 이동은 같은 캐시 키로 묶습니다. */
export const PLACE_BOUNDS_KEY_DIGITS = 3;

/**
 * 한 변(가로·세로)이 이 거리를 넘으면 주변 장소 API를 호출하지 않습니다.
 * 동네 뷰(~수 km)는 허용하고, 시·구 단위로 축소한 광역 조회는 막습니다.
 */
export const MAX_PLACE_BOUNDS_SPAN_METERS = 15_000;

/** 캐시 키·요청 bbox에 쓰는 반올림된 범위 */
export function quantizePlaceBounds(bounds: LatLngBounds, digits = PLACE_BOUNDS_KEY_DIGITS): LatLngBounds {
  const q = (n: number) => Number(n.toFixed(digits));
  return {
    west: q(bounds.west),
    south: q(bounds.south),
    east: q(bounds.east),
    north: q(bounds.north),
  };
}

/** TanStack Query key용 문자열 (west,south,east,north) */
export function placeBoundsCacheKey(bounds: LatLngBounds, digits = PLACE_BOUNDS_KEY_DIGITS): string {
  const q = quantizePlaceBounds(bounds, digits);
  return [q.west, q.south, q.east, q.north].join(",");
}

/** 범위의 가로·세로 길이(m). 가로는 중앙 위도 기준입니다. */
export function placeBoundsSpanMeters(bounds: LatLngBounds): { width: number; height: number } {
  const midLat = (bounds.north + bounds.south) / 2;
  const height = distanceMeters(
    { lat: bounds.south, lng: bounds.west },
    { lat: bounds.north, lng: bounds.west },
  );
  const width = distanceMeters(
    { lat: midLat, lng: bounds.west },
    { lat: midLat, lng: bounds.east },
  );
  return { width, height };
}

/** 광역 축소 등으로 한 번에 너무 넓은 범위를 조회해도 되는지 */
export function isPlaceBoundsQueryAllowed(
  bounds: LatLngBounds,
  maxSpanMeters = MAX_PLACE_BOUNDS_SPAN_METERS,
): boolean {
  const { width, height } = placeBoundsSpanMeters(bounds);
  return width <= maxSpanMeters && height <= maxSpanMeters;
}
