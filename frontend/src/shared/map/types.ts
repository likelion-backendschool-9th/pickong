import type { LatLng } from "@/shared/lib/geo";

export type { LatLng };

export type MarkerTone = "primary" | "muted" | "fixed";

export interface MapMarker {
  id: string;
  position: LatLng;
  /** 핀 안에 표시할 짧은 글자 (번호 등) */
  label?: string;
  /** 핀 아래 작은 이름표 */
  title?: string;
  tone?: MarkerTone;
}
