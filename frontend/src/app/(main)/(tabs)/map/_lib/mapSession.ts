import type { LatLng } from "@/shared/lib/geo";

/** 이번 탭 세션에서 내 지도 최초 GPS를 이미 시도했는지 */
const GPS_BOOT_KEY = "pickong:map-gps-bootstrapped";
/** 마지막으로 보던 지도 중심·줌 (탭 이탈 후 복귀용) */
const CAMERA_KEY = "pickong:map-camera";

export type MapCamera = {
  center: LatLng;
  zoom: number;
};

export function hasBootstrappedMapGps(): boolean {
  if (typeof sessionStorage === "undefined") return false;
  return sessionStorage.getItem(GPS_BOOT_KEY) === "1";
}

/** 최초 GPS 시도가 시작되면 세션에 남겨, 재마운트·재진입 시 자동 재조회를 막습니다. */
export function markMapGpsBootstrapped(): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.setItem(GPS_BOOT_KEY, "1");
}

export function readMapCamera(): MapCamera | null {
  if (typeof sessionStorage === "undefined") return null;
  const raw = sessionStorage.getItem(CAMERA_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<MapCamera>;
    const lat = parsed.center?.lat;
    const lng = parsed.center?.lng;
    const zoom = parsed.zoom;
    if (typeof lat !== "number" || typeof lng !== "number" || typeof zoom !== "number") return null;
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || !Number.isFinite(zoom)) return null;
    return { center: { lat, lng }, zoom };
  } catch {
    return null;
  }
}

export function writeMapCamera(camera: MapCamera): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.setItem(CAMERA_KEY, JSON.stringify(camera));
}
