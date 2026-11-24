import type { LatLng, LatLngBounds } from "../types";

export function toNaverLatLng(p: LatLng): naver.maps.LatLng {
  return new naver.maps.LatLng(p.lat, p.lng);
}

export function fromNaverLatLng(p: naver.maps.LatLng): LatLng {
  return { lat: p.lat(), lng: p.lng() };
}

export function fromNaverBounds(b: naver.maps.LatLngBounds): LatLngBounds {
  const sw = b.getSW();
  const ne = b.getNE();
  return {
    south: sw.lat(),
    west: sw.lng(),
    north: ne.lat(),
    east: ne.lng(),
  };
}

/**
 * 기존 개념 지도의 m/px 축척을 네이버 줌(숫자가 클수록 확대)으로 근사합니다.
 * 일반 동네 뷰(mpp ≈ 2~4) → 줌 15~16 근처.
 */
export function metersPerPixelToZoom(mpp: number): number {
  const zoom = Math.round(17 - Math.log2(Math.max(mpp, 0.5)));
  return Math.min(21, Math.max(5, zoom));
}
