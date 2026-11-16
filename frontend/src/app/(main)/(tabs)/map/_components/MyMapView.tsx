"use client";

import { ChevronRight, Link2, MapPinPlus } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { categoryLabel, formatHours, usePlacesInBounds, type Place, type PlaceCategory, type SavedPlace } from "@/entities/place";
import { SavePlaceButton } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import type { LatLngBounds } from "@/shared/lib/geo";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Badge, Thumb } from "@/shared/ui/display";

const POI = "poi:";

/**
 * 내 지도: 일반 지도처럼 주변 장소를 작은 점으로 깔고, 내가 저장한 곳은 초록 핀으로 따로 표시합니다.
 * 저장하지 않은 장소를 누르면 바로 찜콩할 수 있습니다.
 * (카카오 지도 연결 후에는 지도 자체의 장소 표시를 쓰고, 이 화면은 저장 핀만 올리면 됩니다.)
 */
export function MyMapView({
  saved,
  savedPlaceIds,
  category,
  focus,
}: {
  /** 필터를 거친 저장 장소 */
  saved: SavedPlace[];
  /** 저장한 모든 장소 id (일반 장소 점에서 빼기) */
  savedPlaceIds: Set<string>;
  /** 카테고리 칩: 일반 장소에도 적용 */
  category: PlaceCategory | "ALL";
  /** 검색에서 고른 장소: 그곳으로 이동하고 선택 */
  focus: Place | null;
}) {
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const { data: nearby = [] } = usePlacesInBounds(bounds);
  const [selectedId, setSelectedId] = useState<string | null>(saved[0]?.id ?? null);
  const [addOpen, setAddOpen] = useState(false);

  // 검색에서 장소를 고르면 그 장소를 선택 (렌더 중 상태 보정: 고른 장소가 바뀐 경우에만)
  const [focusedId, setFocusedId] = useState<string | null>(null);
  if (focus && focus.id !== focusedId) {
    setFocusedId(focus.id);
    const mine = saved.find((s) => s.place.id === focus.id);
    setSelectedId(mine ? mine.id : POI + focus.id);
  }

  const pois = useMemo(() => {
    const list = nearby.filter((p) => !savedPlaceIds.has(p.id) && (category === "ALL" || p.category === category));
    if (focus && !savedPlaceIds.has(focus.id) && !list.some((p) => p.id === focus.id)) list.push(focus);
    return list;
  }, [nearby, savedPlaceIds, category, focus]);

  const selectedSaved = saved.find((s) => s.id === selectedId) ?? null;
  const selectedPoi = selectedId?.startsWith(POI) ? (pois.find((p) => POI + p.id === selectedId) ?? null) : null;

  return (
    <div className="relative">
      <div className="pointer-events-none absolute top-3 left-3 z-10 flex items-center gap-2.5 rounded-full bg-white/95 px-3 py-1.5 text-[11px] font-bold text-ink shadow-sm">
        <span className="flex items-center gap-1">
          <span className="size-2.5 rounded-full border border-white bg-primary" /> 저장한 곳 {saved.length}
        </span>
        <span className="flex items-center gap-1 text-ink-sub">
          <span className="size-2 rounded-full bg-ink-faint" /> 주변 장소
        </span>
      </div>
      <MapView
        className="h-[calc(100dvh-260px)] min-h-[440px]"
        markers={[
          ...pois.map((p) => ({ id: POI + p.id, position: p.location, title: p.name, tone: "poi" as const })),
          ...saved.map((s, i) => ({
            id: s.id,
            position: s.place.location,
            label: String(i + 1),
            title: s.place.name,
            tone: s.visitStatus === "VISITED" ? ("muted" as const) : ("primary" as const),
          })),
        ]}
        selectedId={selectedId}
        onMarkerClick={(id) => setSelectedId((cur) => (cur === id ? null : id))}
        onBoundsChange={setBounds}
        focus={focus ? { key: focus.id, position: focus.location } : null}
        caption="예시 지도 · 점은 주변 장소, 초록 핀은 내가 저장한 곳"
      />

      {selectedSaved && (
        <div className="absolute inset-x-3 bottom-3 z-10 rounded-card bg-bg p-4 shadow-[0_2px_16px_rgba(0,0,0,0.12)]">
          <Link href={routes.place(selectedSaved.place.id)} className="flex items-center gap-3">
            <Thumb src={selectedSaved.place.imageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-[16px] font-bold">{selectedSaved.place.name}</span>
                {selectedSaved.visitStatus === "VISITED" && <Badge tone="neutral">방문 완료</Badge>}
              </div>
              <div className="text-[12px] text-ink-sub">
                {selectedSaved.place.region} · {categoryLabel(selectedSaved.place.category)}
              </div>
              <div className="mt-0.5 truncate text-[12px] text-ink-faint">{selectedSaved.source.label}</div>
            </div>
            <ChevronRight size={18} className="text-ink-faint" aria-label="장소 상세 보기" />
          </Link>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <ButtonLink href={routes.newPlan([selectedSaved.place.id])} size="md">
              일정에 추가
            </ButtonLink>
            <Button size="md" variant="secondary" onClick={() => setAddOpen(true)}>
              직접 추가 / 링크
            </Button>
          </div>
        </div>
      )}

      {selectedPoi && (
        <div className="absolute inset-x-3 bottom-3 z-10 flex items-center gap-3 rounded-card bg-bg p-4 shadow-[0_2px_16px_rgba(0,0,0,0.12)]">
          <Link href={routes.place(selectedPoi.id)} className="flex min-w-0 flex-1 items-center gap-3">
            <Thumb src={selectedPoi.imageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[16px] font-bold">{selectedPoi.name}</div>
              <div className="text-[12px] text-ink-sub">
                {selectedPoi.region} · {categoryLabel(selectedPoi.category)}
              </div>
              <div className="mt-0.5 truncate text-[12px] text-ink-faint">{formatHours(selectedPoi.businessHours)}</div>
            </div>
          </Link>
          <SavePlaceButton place={selectedPoi} withLabel source={{ type: "SEARCH", refId: null, label: "내 지도에서 발견" }} />
        </div>
      )}

      <BottomSheet open={addOpen} onClose={() => setAddOpen(false)} title="장소 추가하기">
        <div className="space-y-2 pb-[calc(8px+env(safe-area-inset-bottom))]">
          <ButtonLink href={routes.newPlace()} variant="secondary" block icon={<MapPinPlus size={18} />}>
            장소 직접 추가
          </ButtonLink>
          <ButtonLink href={routes.importLink()} variant="secondary" block icon={<Link2 size={18} />}>
            AI 링크로 가져오기
          </ButtonLink>
        </div>
      </BottomSheet>
    </div>
  );
}
