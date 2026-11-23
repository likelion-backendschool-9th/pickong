import type { LatLng, LatLngBounds } from "../types";

export function toKakaoLatLng(p: LatLng): kakao.maps.LatLng {
  return new kakao.maps.LatLng(p.lat, p.lng);
}

export function fromKakaoLatLng(p: kakao.maps.LatLng): LatLng {
  return { lat: p.getLat(), lng: p.getLng() };
}

export function fromKakaoBounds(b: kakao.maps.LatLngBounds): LatLngBounds {
  const sw = b.getSouthWest();
  const ne = b.getNorthEast();
  return {
    south: sw.getLat(),
    west: sw.getLng(),
    north: ne.getLat(),
    east: ne.getLng(),
  };
}

/**
 * 기존 개념 지도의 m/px 축척을 카카오 레벨(1=가장 확대 ~ 14=가장 축소)로 근사합니다.
 * 일반 동네 뷰(mpp ≈ 2~4) → 레벨 4~5 근처.
 */
export function metersPerPixelToLevel(mpp: number): number {
  const level = Math.round(Math.log2(Math.max(mpp, 0.5)) + 3);
  return Math.min(14, Math.max(1, level));
}
