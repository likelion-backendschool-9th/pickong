import type { LatLng } from "./geo";

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
