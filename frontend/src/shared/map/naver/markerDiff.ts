import type { MapMarker } from "../types";

/** 강조 핀이 이 개수 이하면 선택하지 않아도 이름표를 보여 줍니다. */
export const MAX_PINS_WITH_TITLE = 6;

/** 지도에 그릴 마커 하나의 최종 모습 */
export interface MarkerPlan {
  /** 같은 id가 여러 번 들어와도 겹치지 않는 식별자 */
  key: string;
  /** 모습이 같으면 같은 값. 이 값이 달라진 마커만 다시 그립니다. */
  signature: string;
  marker: MapMarker;
  selected: boolean;
  showTitle: boolean;
}

/** 강조 핀인지 (일반 장소 점과 묶음은 핀 개수에 세지 않습니다) */
const isPin = (m: MapMarker) => m.tone !== "poi" && m.tone !== "cluster";

/** 마커 목록과 선택 상태로 각 마커의 최종 모습을 계산합니다. */
export function planMarkers(markers: readonly MapMarker[], selectedId: string | null | undefined): MarkerPlan[] {
  const pinCount = markers.reduce((n, m) => (isPin(m) ? n + 1 : n), 0);
  const seen = new Map<string, number>();

  return markers.map((marker) => {
    const order = seen.get(marker.id) ?? 0;
    seen.set(marker.id, order + 1);

    const selected = marker.id === selectedId;
    const showTitle =
      !!marker.title &&
      marker.tone !== "cluster" &&
      (selected || (isPin(marker) && pinCount <= MAX_PINS_WITH_TITLE));

    return {
      key: JSON.stringify([marker.id, order]),
      signature: JSON.stringify([
        marker.position.lat,
        marker.position.lng,
        marker.tone ?? null,
        marker.label ?? null,
        marker.title ?? null,
        selected,
        showTitle,
      ]),
      marker,
      selected,
      showTitle,
    };
  });
}

/**
 * 지도에 있는 마커(key → signature)와 새 목록을 비교합니다.
 * 모습이 바뀐 마커는 지우고 다시 그리므로 remove와 add에 모두 들어갑니다.
 */
export function diffMarkers(
  current: ReadonlyMap<string, { signature: string }>,
  next: readonly MarkerPlan[],
): { remove: string[]; add: MarkerPlan[] } {
  const nextSignatures = new Map(next.map((plan) => [plan.key, plan.signature]));
  const remove: string[] = [];
  for (const [key, { signature }] of current) {
    if (nextSignatures.get(key) !== signature) remove.push(key);
  }
  const add = next.filter((plan) => current.get(plan.key)?.signature !== plan.signature);
  return { remove, add };
}
