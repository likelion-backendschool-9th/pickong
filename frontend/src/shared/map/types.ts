import type { LatLng, LatLngBounds } from "@/shared/lib/geo";

export type { LatLng, LatLngBounds };

/** primary: 강조 핀 · muted: 흐린 핀 · fixed: 고정(진한) 핀 · poi: 지도 위 일반 장소(작은 점) · cluster: 장소 묶음(숫자 원) */
export type MarkerTone = "primary" | "muted" | "fixed" | "poi" | "cluster";

export interface MapMarker {
  id: string;
  position: LatLng;
  /** 핀 안에 표시할 짧은 글자 (번호 · 묶음 개수 등) */
  label?: string;
  /** 핀 아래 작은 이름표 */
  title?: string;
  tone?: MarkerTone;
}
