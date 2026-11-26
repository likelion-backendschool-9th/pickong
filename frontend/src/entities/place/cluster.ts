import { distanceMeters } from "@/shared/lib/geo";
import type { PlaceCluster } from "./model";

/** 묶인 장소들이 이 거리 안에 있으면 확대해도 나뉘지 않는 것으로 보고 목록으로 보여 줍니다. */
export const STACKED_CLUSTER_METERS = 10;

/** 확대해도 나뉘지 않는 묶음인지 (같은 건물처럼 좌표가 같거나 거의 같음) */
export function isStackedCluster({ bounds }: Pick<PlaceCluster, "bounds">): boolean {
  const diagonal = distanceMeters(
    { lat: bounds.south, lng: bounds.west },
    { lat: bounds.north, lng: bounds.east },
  );
  return diagonal <= STACKED_CLUSTER_METERS;
}

/** 묶음 원 안에 쓰는 짧은 개수: 999 → "999", 1234 → "1.2천", 34000 → "3.4만" */
export function formatClusterCount(count: number): string {
  if (count < 1000) return String(count);
  const [unit, label] = count < 10_000 ? [1000, "천"] : [10_000, "만"];
  return `${Math.floor((count / unit) * 10) / 10}${label}`;
}
