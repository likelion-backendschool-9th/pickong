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

/** GeolocationPositionError.code → 사용자용 메시지 */
export function geolocationErrorMessage(code: number): string {
  switch (code) {
    case 1:
      return "위치 권한이 꺼져 있어요. 브라우저 설정에서 허용해 주세요.";
    case 2:
      return "지금 위치를 확인할 수 없어요. 잠시 후 다시 시도해 주세요.";
    case 3:
      return "위치를 찾는 데 시간이 너무 걸렸어요. 다시 시도해 주세요.";
    default:
      return "위치를 불러오지 못했어요.";
  }
}

/**
 * 브라우저 GPS로 현재 좌표를 한 번만 읽습니다.
 * 권한은 호출 시점에 요청합니다 (상시 추적용이 아닙니다).
 */
export function getCurrentPosition(): Promise<LatLng> {
  if (typeof navigator === "undefined" || !navigator.geolocation) {
    return Promise.reject(new Error("이 환경에서는 위치를 사용할 수 없어요."));
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      (err) => {
        reject(new Error(geolocationErrorMessage(err.code)));
      },
      {
        enableHighAccuracy: true,
        timeout: 12_000,
        maximumAge: 30_000,
      },
    );
  });
}
