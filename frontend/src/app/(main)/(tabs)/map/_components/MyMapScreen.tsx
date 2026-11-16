"use client";

import { Folder, List, Map as MapIcon, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { FILTER_CATEGORIES, useMapCategories, useSavedPlaces, type Place, type PlaceCategory } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { Chip } from "@/shared/ui/controls";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { PageHeader } from "@/shared/ui/layout";
import { EMPTY_FILTER, filterSavedPlaces, type SavedPlaceFilter } from "../_lib/filterSavedPlaces";
import { CategoryChipEditor } from "./CategoryChipEditor";
import { activeFilterCount, MapFilterSheet } from "./MapFilterSheet";
import { MyMapView } from "./MyMapView";
import { PlaceSearchResults } from "./PlaceSearchResults";
import { SavedListView } from "./SavedListView";

/**
 * 내 지도: 일반 지도처럼 주변 장소가 보이고, 내가 저장한 곳은 초록 핀으로 따로 표시합니다.
 * 검색은 기본이 일반 장소 검색이고, 필터에서 ‘저장한 곳만’을 켜면 저장한 장소 안에서만 찾습니다.
 * 지도를 넓게 쓰도록 상단은 3줄(제목 / 검색 + 필터 / 컬렉션)로 두고, 카테고리는 필터 패널에 있습니다.
 * 목록 보기는 저장한 장소 관리(컬렉션 · 방문 상태 · 삭제)입니다.
 */
export function MyMapScreen() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const view = params.get("view") === "list" ? "list" : "map";
  const [filter, setFilter] = useState<SavedPlaceFilter>(() => ({
    ...EMPTY_FILTER,
    collectionId: params.get("collection") ?? "ALL",
  }));
  const { data: saved, isPending, isError, error, refetch } = useSavedPlaces();
  const { data: collections = [] } = useMyCollections();
  const { data: chipCategories = FILTER_CATEGORIES } = useMapCategories();
  const [editingChips, setEditingChips] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [savedOnly, setSavedOnly] = useState(false);
  /** 일반 검색에서 고른 장소 (지도를 그곳으로 옮김) */
  const [picked, setPicked] = useState<Place | null>(null);
  const searchingAll = !savedOnly && query.trim().length > 0 && !picked;
  const filterCount = activeFilterCount({ savedOnly, category: filter.category });

  // 검색어는 ‘저장한 곳만’일 때만 저장 장소 필터에 씁니다.
  const effective = useMemo(() => ({ ...filter, query: savedOnly ? query : "" }), [filter, query, savedOnly]);
  const filtered = useMemo(() => filterSavedPlaces(saved ?? [], effective), [saved, effective]);
  const savedPlaceIds = useMemo(() => new Set((saved ?? []).map((s) => s.place.id)), [saved]);
  const set = (patch: Partial<SavedPlaceFilter>) => setFilter((f) => ({ ...f, ...patch }));
  const setView = (v: "map" | "list") => router.replace(v === "list" ? `${pathname}?view=list` : pathname, { scroll: false });

  return (
    <div>
      {/* 지도 우선형 상단 3줄: 제목 + 컬렉션 링크 / 검색 + 필터 / 컬렉션 칩 + 목록·지도 전환 */}
      <header className="sticky top-0 z-30 bg-bg">
        <PageHeader
          compact
          label="My Map"
          title="내 지도"
          right={
            <Link href={routes.collections()} className="inline-flex h-9 items-center gap-1.5 rounded-full px-2 text-[14px] font-bold text-primary-deep active:bg-accent-soft">
              <Folder size={17} strokeWidth={1.9} /> 컬렉션
            </Link>
          }
        />
        <div className="flex items-center gap-2 px-5 pt-2.5">
          <div className="min-w-0 flex-1">
            <SearchInput
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPicked(null);
              }}
              placeholder={savedOnly ? "저장한 장소 검색" : "장소 · 지역 검색"}
              aria-label={savedOnly ? "저장한 장소 검색" : "장소 검색"}
            />
          </div>
          <button
            type="button"
            onClick={() => setFilterOpen(true)}
            aria-haspopup="dialog"
            aria-label={filterCount ? `필터, ${filterCount}개 적용 중` : "필터"}
            className={cn(
              "inline-flex h-[46px] shrink-0 items-center gap-1.5 rounded-[13px] px-3.5 text-[14px] font-bold",
              filterCount ? "bg-accent-soft text-primary-deep" : "bg-fill text-ink",
            )}
          >
            <SlidersHorizontal size={17} />
            필터{filterCount > 0 && <span className="tabular-nums">{filterCount}</span>}
          </button>
        </div>
        <div className="flex items-center gap-2 py-2.5 pr-3 pl-5">
          <div className="scrollbar-none flex min-w-0 flex-1 gap-2 overflow-x-auto">
            <Chip variant="solid" className="h-9 px-4 text-[14px]" selected={filter.collectionId === "ALL"} onClick={() => set({ collectionId: "ALL" })}>
              전체 {saved?.length ?? ""}
            </Chip>
            {collections.map((c) => (
              <Chip key={c.id} variant="solid" className="h-9 px-4 text-[14px]" selected={filter.collectionId === c.id} onClick={() => set({ collectionId: c.id })}>
                {c.title}
              </Chip>
            ))}
            <Chip variant="solid" className="h-9 px-4 text-[14px]" selected={filter.collectionId === "NONE"} onClick={() => set({ collectionId: "NONE" })}>
              컬렉션 없음
            </Chip>
          </div>
          <button
            type="button"
            onClick={() => setView(view === "map" ? "list" : "map")}
            aria-label={view === "map" ? "목록으로 보기" : "지도로 보기"}
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-accent-soft text-primary-deep active:brightness-95"
          >
            {view === "map" ? <List size={17} /> : <MapIcon size={17} />}
          </button>
        </div>
      </header>

      {searchingAll ? (
        <PlaceSearchResults
          query={query}
          savedPlaceIds={savedPlaceIds}
          onPick={(place) => {
            setPicked(place);
            if (view === "list") setView("map");
          }}
        />
      ) : isPending ? (
        <Skeleton className="h-[calc(100dvh-250px)] rounded-none" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : view === "map" ? (
        <MyMapView saved={filtered} savedPlaceIds={savedPlaceIds} category={filter.category} focus={picked} />
      ) : (
        <SavedListView saved={filtered} total={saved.length} filter={effective} onFilter={set} />
      )}

      <MapFilterSheet
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        value={{ savedOnly, category: filter.category }}
        onChange={(patch) => {
          if (patch.savedOnly !== undefined) {
            setSavedOnly(patch.savedOnly);
            setPicked(null);
          }
          if (patch.category !== undefined) set({ category: patch.category });
        }}
        categories={chipCategories}
        onEditCategories={() => {
          setFilterOpen(false);
          setEditingChips(true);
        }}
      />
      <CategoryChipEditor
        open={editingChips}
        onClose={() => setEditingChips(false)}
        current={chipCategories}
        onSaved={(categories: PlaceCategory[]) => {
          // 보고 있던 카테고리를 숨겼으면 전체로
          if (filter.category !== "ALL" && !categories.includes(filter.category)) set({ category: "ALL" });
        }}
      />
    </div>
  );
}
