"use client";

import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { categoryLabel, useSavedPlaces, type Place, type PlaceCategory } from "@/entities/place";
import { PlaceSearchPanel } from "@/features/place-search";
import { errorMessage } from "@/shared/api/errors";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { CheckMark, Chip } from "@/shared/ui/controls";
import { EmptyState, Notice, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";

/**
 * 2단계: 갈 곳 고르기. 내 지도에 저장한 곳에서 고르거나 검색해서 넣습니다.
 * 고른 순서대로 일정에 들어가고, 순서 · 고정 시간은 다음 화면에서 정합니다.
 */
export function CandidatesStep({
  selected,
  onChange,
  onBack,
  onSubmit,
  submitting,
  error,
}: {
  selected: Place[];
  onChange: (places: Place[]) => void;
  onBack: () => void;
  onSubmit: () => void;
  submitting: boolean;
  error: unknown;
}) {
  const { data: saved, isPending } = useSavedPlaces();
  const [region, setRegion] = useState<string>("ALL");
  const [category, setCategory] = useState<PlaceCategory | "ALL">("ALL");
  const [searching, setSearching] = useState(false);

  const places = useMemo(() => (saved ?? []).map((s) => s.place), [saved]);
  // 검색으로 넣은 곳(저장하지 않은 곳)도 목록에 보이게
  const all = useMemo(() => [...places, ...selected.filter((p) => !places.some((x) => x.id === p.id))], [places, selected]);
  const regions = [...new Set(all.map((p) => p.region))];
  const categories = [...new Set(all.map((p) => p.category))];
  const visible = all.filter((p) => (region === "ALL" || p.region === region) && (category === "ALL" || p.category === category));

  const isSelected = (id: string) => selected.some((p) => p.id === id);
  const toggle = (place: Place) => onChange(isSelected(place.id) ? selected.filter((p) => p.id !== place.id) : [...selected, place]);

  return (
    <Screen>
      <TopBar title="갈 곳 고르기" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <p className="text-[12px] font-bold text-primary-deep">2 / 2 · 갈 곳</p>
        <h2 className="mt-2 text-[20px] font-bold">어디를 들러 볼까요?</h2>
        <p className="mt-1 text-[13px] text-ink-sub">순서와 예약 시간은 다음 화면에서 정해요.</p>

        <Button variant="outline" size="md" block className="mt-4" icon={<Search size={16} className="text-primary-deep" />} onClick={() => setSearching(true)}>
          장소 검색해서 넣기
        </Button>

        {all.length > 0 && (
          <>
            <div className="scrollbar-none -mx-5 mt-4 flex gap-1.5 overflow-x-auto px-5">
              <Chip selected={region === "ALL"} onClick={() => setRegion("ALL")}>전체 지역</Chip>
              {regions.map((r) => (
                <Chip key={r} selected={region === r} onClick={() => setRegion(r)}>{r}</Chip>
              ))}
            </div>
            <div className="scrollbar-none -mx-5 mt-2 flex gap-1.5 overflow-x-auto px-5">
              <Chip selected={category === "ALL"} onClick={() => setCategory("ALL")}>전체</Chip>
              {categories.map((c) => (
                <Chip key={c} selected={category === c} onClick={() => setCategory(c)}>{categoryLabel(c)}</Chip>
              ))}
            </div>
          </>
        )}

        {isPending ? (
          <Skeleton className="mt-4 h-40" />
        ) : all.length === 0 ? (
          <EmptyState title="저장한 장소가 없어요" description="위에서 장소를 검색해서 넣어 보세요." />
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {visible.map((p) => {
              const order = selected.findIndex((x) => x.id === p.id);
              return (
                <li key={p.id}>
                  <button type="button" role="checkbox" aria-checked={order >= 0} onClick={() => toggle(p)} className="flex w-full items-center gap-3 py-3 text-left">
                    <CheckMark checked={order >= 0} round />
                    <span className="min-w-0 flex-1">
                      <span className="block text-[14px] font-bold">{p.name}</span>
                      <span className="block text-[12px] text-ink-sub">
                        {p.region} · {categoryLabel(p.category)}
                      </span>
                    </span>
                    {order >= 0 && <span className="text-[12px] font-bold text-primary-deep tabular-nums">{order + 1}번째</span>}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        {!!error && <Notice tone="danger" className="mt-3">{errorMessage(error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={submitting} disabled={selected.length === 0} onClick={onSubmit}>
          일정 만들기 · {selected.length}곳
        </Button>
      </BottomCta>

      <BottomSheet open={searching} onClose={() => setSearching(false)} title="장소 검색" className="h-[85dvh]">
        {searching && (
          <PlaceSearchPanel
            autoFocus
            onSelect={(place) => {
              if (!isSelected(place.id)) onChange([...selected, place]);
              setSearching(false);
            }}
          />
        )}
      </BottomSheet>
    </Screen>
  );
}
