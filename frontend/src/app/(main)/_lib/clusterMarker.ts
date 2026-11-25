import { formatClusterCount, type PlaceCluster } from "@/entities/place";
import type { MapMarker } from "@/shared/map";

const CLUSTER = "cluster:";

/** 장소 묶음을 숫자 원 마커로 바꿉니다. */
export const toClusterMarker = (cluster: PlaceCluster): MapMarker => ({
  id: CLUSTER + cluster.id,
  position: cluster.location,
  label: formatClusterCount(cluster.count),
  title: `장소 ${cluster.count.toLocaleString("ko-KR")}곳`,
  tone: "cluster",
});

/** 누른 마커가 묶음이면 그 묶음 */
export const findCluster = (clusters: readonly PlaceCluster[], markerId: string): PlaceCluster | null =>
  markerId.startsWith(CLUSTER) ? (clusters.find((c) => CLUSTER + c.id === markerId) ?? null) : null;
