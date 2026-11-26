"use client";

import { PlaceListItem, usePlacesInCluster, type Place, type PlaceCategory, type PlaceCluster } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { ErrorState, Skeleton } from "@/shared/ui/display";

/** 확대해도 나뉘지 않는 묶음(같은 건물 등)의 장소 목록. 고르면 onSelect로 알립니다. */
export function PlaceClusterSheet({
  cluster,
  category = "ALL",
  onClose,
  onSelect,
}: {
  /** null이면 닫힘 */
  cluster: PlaceCluster | null;
  category?: PlaceCategory | "ALL";
  onClose: () => void;
  onSelect: (place: Place) => void;
}) {
  const { data, isPending, isError, error } = usePlacesInCluster(cluster, category);
  const rest = cluster && data ? cluster.count - data.length : 0;

  return (
    <BottomSheet
      open={!!cluster}
      onClose={onClose}
      title={cluster ? `이 위치의 장소 ${cluster.count.toLocaleString("ko-KR")}곳` : undefined}
    >
      {isError ? (
        <ErrorState message={errorMessage(error)} />
      ) : isPending ? (
        <Skeleton className="h-40" />
      ) : (
        <div className="pb-[calc(8px+env(safe-area-inset-bottom))]">
          {data.map((place) => (
            <PlaceListItem key={place.id} place={place} onClick={() => onSelect(place)} />
          ))}
          {rest > 0 && (
            <p className="py-2 text-center text-[12px] text-ink-faint">외 {rest.toLocaleString("ko-KR")}곳이 더 있어요</p>
          )}
        </div>
      )}
    </BottomSheet>
  );
}
