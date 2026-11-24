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

/** 래스터 6~21, GL 벡터맵 5~20 */
export function clampNaverZoom(zoom: number, gl: boolean): number {
  const min = 5;
  const max = gl ? 20 : 21;
  return Math.min(max, Math.max(min, zoom));
}

/**
 * 기존 개념 지도의 m/px 축척을 네이버 줌(숫자가 클수록 확대)으로 근사합니다.
 * 일반 동네 뷰(mpp ≈ 2~4) → 줌 15~16 근처.
 * GL 벡터맵은 같은 화면이 대략 1레벨 작게 보이는 편이어서 보정합니다.
 */
export function metersPerPixelToZoom(mpp: number, gl = false): number {
  const zoom = Math.round(17 - Math.log2(Math.max(mpp, 0.5)));
  return clampNaverZoom(gl ? zoom - 1 : zoom, gl);
}
