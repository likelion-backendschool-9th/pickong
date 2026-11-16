export interface LatLng {
  lat: number;
  lng: number;
}

/** 지도 화면에 보이는 범위 */
export interface LatLngBounds {
  north: number;
  south: number;
  east: number;
  west: number;
}

/** 좌표가 범위 안에 있는지 */
export function inBounds(p: LatLng, b: LatLngBounds): boolean {
  return p.lat <= b.north && p.lat >= b.south && p.lng <= b.east && p.lng >= b.west;
}

/** 두 좌표 사이 직선거리 (m) */
export function distanceMeters(a: LatLng, b: LatLng): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/** 직선거리 기반 이동 시간 추정치 (분). 실제 계산 결과가 오기 전 임시값으로만 사용합니다. */
export function estimateTravelMinutes(a: LatLng, b: LatLng): number {
  const meters = distanceMeters(a, b) * 1.3;
  return meters <= 2000 ? Math.max(3, Math.round(meters / 75)) : Math.round(12 + meters / 333);
}
