"use client";

import { ChevronRight } from "lucide-react";
import { categoryLabel, usePlaceSearch, type Place } from "@/entities/place";
import { SavePlaceButton } from "@/features/save-place";
import { useDebounced } from "@/shared/lib/useDebounced";
import { Badge, EmptyState, Spinner, Thumb } from "@/shared/ui/display";

/**
 * 일반 장소 검색 결과 (카카오맵 검색처럼 모든 장소). 저장한 곳은 배지로 표시하고, 바로 찜콩할 수 있습니다.
 * 결과를 누르면 지도에서 그 장소를 보여 줍니다.
 */
export function PlaceSearchResults({
  query,
  savedPlaceIds,
  onPick,
}: {
  query: string;
  savedPlaceIds: Set<string>;
  onPick: (place: Place) => void;
}) {
  const debounced = useDebounced(query.trim());
  const { data: results, isFetching } = usePlaceSearch(debounced);
  const waiting = debounced !== query.trim() || (isFetching && !results);

  if (waiting) {
    return (
      <div className="flex justify-center py-10">
        <Spinner className="size-6" label="장소 찾는 중" />
      </div>
    );
  }
  if (!results?.length) {
    return <EmptyState title={`‘${debounced}’ 검색 결과가 없어요`} description="다른 이름이나 지역으로 찾아보세요." />;
  }
  return (
    <div aria-live="polite">
      <p className="px-4 pt-1 pb-1 text-[12px] text-ink-faint">장소 {results.length}곳 · 누르면 지도에서 보여요</p>
      <ul className="divide-y divide-line">
        {results.map((place) => (
          <li key={place.id} className="flex items-center gap-2 px-4">
            <button type="button" onClick={() => onPick(place)} className="flex min-w-0 flex-1 items-center gap-3 py-3 text-left">
              <Thumb src={place.imageUrl} alt="" className="size-12" />
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1.5">
                  <span className="truncate text-[14px] font-bold">{place.name}</span>
                  {savedPlaceIds.has(place.id) && <Badge>저장한 곳</Badge>}
                </span>
                <span className="block truncate text-[12px] text-ink-sub">
                  {place.region} · {categoryLabel(place.category)}
                </span>
              </span>
              <ChevronRight size={16} className="shrink-0 text-ink-faint" />
            </button>
            <SavePlaceButton place={place} source={{ type: "SEARCH", refId: null, label: "내 지도 검색에서 저장" }} />
          </li>
        ))}
      </ul>
    </div>
  );
}
