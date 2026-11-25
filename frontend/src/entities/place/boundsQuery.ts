import { distanceMeters, type LatLngBounds } from "@/shared/lib/geo";

/** 소수 셋째 자리 ≈ 100m. 작은 이동은 같은 캐시 키로 묶습니다. */
export const PLACE_BOUNDS_KEY_DIGITS = 3;

/**
 * 한 변(가로·세로)이 이 거리를 넘으면 주변 장소 API를 호출하지 않습니다.
 * 동네 뷰(~수 km)는 허용하고, 시·구 단위로 축소한 광역 조회는 막습니다.
 */
export const MAX_PLACE_BOUNDS_SPAN_METERS = 15_000;

/**
 * 캐시 키·요청 bbox에 쓰는 범위. 화면을 모두 덮도록 바깥쪽으로 넓힙니다.
 * 반올림하면 크게 확대했을 때 범위가 화면보다 작아지거나 0이 되어 장소가 빠집니다.
 */
export function quantizePlaceBounds(bounds: LatLngBounds, digits = PLACE_BOUNDS_KEY_DIGITS): LatLngBounds {
  const scale = 10 ** digits;
  // 127.056 * 1000이 127056.00000000001이 되는 식의 부동소수점 오차로 한 칸 더 넓어지지 않게 먼저 다듬습니다.
  const scaled = (n: number) => Number((n * scale).toFixed(6));
  const down = (n: number) => Math.floor(scaled(n)) / scale;
  const up = (n: number) => Math.ceil(scaled(n)) / scale;
  return {
    west: down(bounds.west),
    south: down(bounds.south),
    east: up(bounds.east),
    north: up(bounds.north),
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
