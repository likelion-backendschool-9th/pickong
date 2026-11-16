"use client";

import { FolderHeart, List, Map as MapIcon, Search, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { CATEGORY_LABEL, FILTER_CATEGORIES, useMapCategories, useSavedPlaces, type Place, type PlaceCategory } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { CheckMark, Chip } from "@/shared/ui/controls";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
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
        <div className="flex h-[52px] items-center px-4">
          <h1 className="text-[20px] font-bold">내 지도</h1>
          <Link href={routes.collections()} className="ml-auto inline-flex h-9 items-center gap-1 rounded-full px-3 text-[13px] font-bold text-primary-deep active:bg-soft">
            <FolderHeart size={16} /> 컬렉션
          </Link>
        </div>
        <div className="px-4">
          <div className="relative">
            <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
            <Input
              type="search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPicked(null);
              }}
              placeholder={savedOnly ? "저장한 장소 검색" : "장소 · 지역 검색"}
              aria-label={savedOnly ? "저장한 장소 검색" : "장소 검색"}
              className="h-11 pr-[104px] pl-10"
            />
            <button
              type="button"
              role="checkbox"
              aria-checked={savedOnly}
              onClick={() => {
                setSavedOnly((v) => !v);
                setPicked(null);
              }}
              className="absolute top-1/2 right-1.5 flex h-8 -translate-y-1/2 items-center gap-1.5 rounded-full px-2 text-[12px] font-bold text-ink-sub active:bg-fill"
            >
              <CheckMark checked={savedOnly} />
              저장한 곳만
            </button>
          </div>
        </div>
        <div className="scrollbar-none flex gap-2 overflow-x-auto px-4 pt-3">
          <Chip selected={filter.collectionId === "ALL"} onClick={() => set({ collectionId: "ALL" })}>
            전체 {saved?.length ?? ""}
          </Chip>
          {collections.map((c) => (
            <Chip key={c.id} selected={filter.collectionId === c.id} onClick={() => set({ collectionId: c.id })}>
              {c.title}
            </Chip>
          ))}
          <Chip selected={filter.collectionId === "NONE"} onClick={() => set({ collectionId: "NONE" })}>
            컬렉션 없음
          </Chip>
        </div>
        <div className="flex items-center gap-2 px-4 py-2.5">
          <div className="scrollbar-none flex min-w-0 flex-1 gap-1.5 overflow-x-auto">
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
              className="inline-flex h-7 shrink-0 items-center rounded-md px-2 text-ink-sub active:bg-fill"
            >
              <SlidersHorizontal size={15} />
            </button>
          </div>
          <Chip onClick={() => setView(view === "map" ? "list" : "map")} icon={view === "map" ? <List size={14} /> : <MapIcon size={14} />}>
            {view === "map" ? "목록" : "지도"}
          </Chip>
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
        <Skeleton className="h-[calc(100dvh-260px)] rounded-none" />
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

function CategoryChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={
        active
          ? "h-7 shrink-0 rounded-md bg-selected px-2.5 text-[12px] font-bold text-primary-deep"
          : "h-7 shrink-0 rounded-md px-2.5 text-[12px] text-ink-sub active:bg-fill"
      }
    >
      {children}
    </button>
  );
}
