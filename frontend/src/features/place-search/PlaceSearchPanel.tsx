"use client";

import { ChevronRight, MapPin, Search } from "lucide-react";
import { useState, type ReactNode } from "react";
import { categoryLabel, usePlaceSearch, type Place } from "@/entities/place";
import { cn } from "@/shared/lib/cn";
import { Spinner } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { useDebounced } from "@/shared/lib/useDebounced";

interface PlaceSearchPanelProps {
  onSelect: (place: Place) => void;
  /** 검색어가 없을 때 보여줄 최근 사용 장소 */
  recent?: { place: Place; note: string }[];
  /** 최근 사용 장소 목록의 제목 */
  recentTitle?: string;
  placeholder?: string;
  /** 결과 하단 보조 행동 (예: 지도에서 직접 지정) */
  footer?: ReactNode;
  autoFocus?: boolean;
  initialQuery?: string;
  /** 결과를 상위 4개 높이까지만 보여 주고 나머지는 스크롤 (아래 내용이 보이도록) */
  compactResults?: boolean;
}

/** 장소 검색: 게시물 장소 연결 · 직접 추가 · 예약 장소 · 링크 후보 위치 수정에서 공통 사용 */
export function PlaceSearchPanel({
  onSelect,
  recent = [],
  recentTitle = "최근 사용한 장소",
  placeholder = "장소 이름이나 지역 검색",
  footer,
  autoFocus,
  initialQuery = "",
  compactResults = false,
}: PlaceSearchPanelProps) {
  const [query, setQuery] = useState(initialQuery);
  const debounced = useDebounced(query.trim());
  const { data: results, isFetching } = usePlaceSearch(debounced);

  return (
    <div>
      <div className="relative">
        <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
        <Input
          type="search"
          autoFocus={autoFocus}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={placeholder}
          aria-label="장소 검색"
          className="pl-10"
        />
        {isFetching && <Spinner className="absolute top-1/2 right-3.5 size-4 -translate-y-1/2" />}
      </div>

      <div className={cn(compactResults ? "mt-2 max-h-[232px] overflow-y-auto overscroll-contain" : "mt-4")}>
        {!debounced && recent.length > 0 && (
          <>
            <h3 className="mb-1 text-[13px] font-bold">{recentTitle}</h3>
            {recent.map(({ place, note }) => (
              <ResultRow key={place.id} place={place} meta={note} onClick={() => onSelect(place)} />
            ))}
          </>
        )}

        {debounced && results && results.length === 0 && !isFetching && (
          <p className="py-6 text-center text-[13px] text-ink-sub">‘{debounced}’ 검색 결과가 없어요.</p>
        )}

        {debounced &&
          results?.map((place) => (
            <ResultRow
              key={place.id}
              place={place}
              meta={`${place.region} · ${categoryLabel(place.category)}`}
              onClick={() => onSelect(place)}
            />
          ))}
      </div>

      {footer && <div className="mt-3 border-t border-line pt-3">{footer}</div>}
    </div>
  );
}

function ResultRow({ place, meta, onClick }: { place: Place; meta: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex min-h-14 w-full items-center gap-3 py-2 text-left active:bg-fill">
      <MapPin size={18} strokeWidth={1.6} className="shrink-0 text-primary-deep" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-bold">{place.name}</span>
        <span className="block truncate text-[12px] text-ink-sub">{meta}</span>
      </span>
      <ChevronRight size={18} className="shrink-0 text-ink-faint" />
    </button>
  );
}
