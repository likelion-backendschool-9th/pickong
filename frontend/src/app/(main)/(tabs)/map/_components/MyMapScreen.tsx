"use client";

import { Folder, List, Map as MapIcon, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { CATEGORY_LABEL, FILTER_CATEGORIES, useMapCategories, useSavedPlaces, type Place, type PlaceCategory } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Chip, Switch } from "@/shared/ui/controls";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { PageHeader } from "@/shared/ui/layout";
import { EMPTY_FILTER, filterSavedPlaces, type SavedPlaceFilter } from "../_lib/filterSavedPlaces";
import { CategoryChipEditor } from "./CategoryChipEditor";
import { MyMapView } from "./MyMapView";
import { PlaceSearchResults } from "./PlaceSearchResults";
import { SavedListView } from "./SavedListView";

/**
 * 내 지도: 일반 지도처럼 주변 장소가 보이고, 내가 저장한 곳은 초록 핀으로 따로 표시합니다.
 * 검색은 기본이 일반 장소 검색이고, ‘저장한 곳만’을 켜면 저장한 장소 안에서만 찾습니다.
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
  const [query, setQuery] = useState("");
  const [savedOnly, setSavedOnly] = useState(false);
  /** 일반 검색에서 고른 장소 (지도를 그곳으로 옮김) */
  const [picked, setPicked] = useState<Place | null>(null);
  const searchingAll = !savedOnly && query.trim().length > 0 && !picked;

  // 검색어는 ‘저장한 곳만’일 때만 저장 장소 필터에 씁니다.
  const effective = useMemo(() => ({ ...filter, query: savedOnly ? query : "" }), [filter, query, savedOnly]);
  const filtered = useMemo(() => filterSavedPlaces(saved ?? [], effective), [saved, effective]);
  const savedPlaceIds = useMemo(() => new Set((saved ?? []).map((s) => s.place.id)), [saved]);
  const set = (patch: Partial<SavedPlaceFilter>) => setFilter((f) => ({ ...f, ...patch }));
  const setView = (v: "map" | "list") => router.replace(v === "list" ? `${pathname}?view=list` : pathname, { scroll: false });

  return (
    <div>
      <header className="sticky top-0 z-30 bg-bg">
        <PageHeader
          label="My Map"
          title="내 지도"
          right={
            <Link href={routes.collections()} className="inline-flex h-9 items-center gap-1.5 rounded-full px-2 text-[14px] font-bold text-primary-deep active:bg-accent-soft">
              <Folder size={17} strokeWidth={1.9} /> 컬렉션
            </Link>
          }
        />
        <div className="px-5 pt-4">
          <SearchInput
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPicked(null);
            }}
            placeholder={savedOnly ? "저장한 장소 검색" : "장소 · 지역 검색"}
            aria-label={savedOnly ? "저장한 장소 검색" : "장소 검색"}
          />
          <Switch
            label="저장한 곳만"
            checked={savedOnly}
            onChange={(v) => {
              setSavedOnly(v);
              setPicked(null);
            }}
            className="mt-1.5"
          />
        </div>
        <div className="scrollbar-none flex gap-2 overflow-x-auto px-5 pt-1.5">
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
        <div className="mt-2 flex items-center gap-2 border-b border-line pr-3 pl-5">
          <div className="scrollbar-none flex min-w-0 flex-1 gap-5 overflow-x-auto">
            <CategoryChip active={filter.category === "ALL"} onClick={() => set({ category: "ALL" })}>전체</CategoryChip>
            {chipCategories.map((c) => (
              <CategoryChip key={c} active={filter.category === c} onClick={() => set({ category: c })}>
                {CATEGORY_LABEL[c]}
              </CategoryChip>
            ))}
            <button
              type="button"
              onClick={() => setEditingChips(true)}
              aria-label="카테고리 칩 편집"
              className="inline-flex h-11 shrink-0 items-center px-1 text-ink-sub active:text-ink"
            >
              <SlidersHorizontal size={15} />
            </button>
          </div>
          <button
            type="button"
            onClick={() => setView(view === "map" ? "list" : "map")}
            className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full bg-accent-soft px-3 text-[13px] font-bold text-primary-deep active:brightness-95"
          >
            {view === "map" ? <List size={14} /> : <MapIcon size={14} />}
            {view === "map" ? "목록" : "지도"}
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
        <Skeleton className="h-[calc(100dvh-345px)] rounded-none" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : view === "map" ? (
        <MyMapView saved={filtered} savedPlaceIds={savedPlaceIds} category={filter.category} focus={picked} />
      ) : (
        <SavedListView saved={filtered} total={saved.length} filter={effective} onFilter={set} />
      )}

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

/** 카테고리: 선택한 항목만 초록 글씨 + 밑줄 */
function CategoryChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={
        active
          ? "relative h-11 shrink-0 text-[14px] font-bold text-primary-deep"
          : "relative h-11 shrink-0 text-[14px] text-ink-sub active:text-ink"
      }
    >
      {children}
      {active && <span className="absolute inset-x-0 bottom-0 h-[2px] rounded-full bg-primary-deep" aria-hidden />}
    </button>
  );
}
