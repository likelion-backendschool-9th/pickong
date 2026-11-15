"use client";

import { FolderHeart, List, Map as MapIcon, Search } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { CATEGORY_LABEL, FILTER_CATEGORIES, useSavedPlaces } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Chip } from "@/shared/ui/controls";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { EMPTY_FILTER, filterSavedPlaces, type SavedPlaceFilter } from "../_lib/filterSavedPlaces";
import { EmptyMap } from "./EmptyMap";
import { SavedMapView } from "./SavedMapView";
import { SavedListView } from "./SavedListView";

/** 내 지도: 내가 저장한 장소만 보여줍니다. 지도와 목록은 같은 저장 데이터를 사용합니다. */
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

  const filtered = useMemo(() => filterSavedPlaces(saved ?? [], filter), [saved, filter]);
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
              value={filter.query}
              onChange={(e) => set({ query: e.target.value })}
              placeholder="저장한 장소 검색"
              aria-label="저장한 장소 검색"
              className="h-11 pl-10"
            />
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
            {FILTER_CATEGORIES.map((c) => (
              <CategoryChip key={c} active={filter.category === c} onClick={() => set({ category: c })}>
                {CATEGORY_LABEL[c]}
              </CategoryChip>
            ))}
          </div>
          <Chip onClick={() => setView(view === "map" ? "list" : "map")} icon={view === "map" ? <List size={14} /> : <MapIcon size={14} />}>
            {view === "map" ? "목록" : "지도"}
          </Chip>
        </div>
      </header>

      {isPending ? (
        <Skeleton className="h-[calc(100dvh-260px)] rounded-none" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : saved.length === 0 ? (
        <EmptyMap />
      ) : view === "map" ? (
        <SavedMapView saved={filtered} />
      ) : (
        <SavedListView saved={filtered} total={saved.length} filter={filter} onFilter={set} />
      )}
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
