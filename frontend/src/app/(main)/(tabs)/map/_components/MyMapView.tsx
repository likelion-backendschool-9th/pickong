"use client";

import { ChevronDown, ChevronUp, Info, Link2, MapPinPlus } from "lucide-react";
import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { categoryLabel, formatHours, usePlacesInBounds, type Place, type PlaceCategory, type SavedPlace } from "@/entities/place";
import { SavePlaceButton } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import type { LatLng, LatLngBounds } from "@/shared/lib/geo";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Badge, Thumb } from "@/shared/ui/display";

const POI = "poi:";

type FocusRequest = { key: string; position: LatLng; keepZoom?: boolean } | null;

/**
 * 내 지도 (지도 우선형): 주변 장소는 작은 점, 내가 저장한 곳은 초록 핀.
 * 장소를 누르면 지도가 그 핀으로 옮겨지고(카드에 가리지 않게), 아래에 작은 요약 카드가 뜹니다.
 * 카드를 누르면 펼쳐져 저장한 곳은 ‘일정에 추가 · 직접 추가/링크’, 주변 장소는 영업시간 · 상세 보기가 보입니다.
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
  /** 카테고리 필터: 주변 장소에도 적용 */
  category: PlaceCategory | "ALL";
  /** 검색에서 고른 장소: 그곳으로 이동하고 선택 */
  focus: Place | null;
}) {
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const { data: nearby = [] } = usePlacesInBounds(bounds);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [legendOpen, setLegendOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [mapFocus, setMapFocus] = useState<FocusRequest>(null);

  // 검색에서 장소를 고르면 그 장소로 이동해 선택 (렌더 중 상태 보정: 고른 장소가 바뀐 경우에만)
  const [focusedId, setFocusedId] = useState<string | null>(null);
  if (focus && focus.id !== focusedId) {
    setFocusedId(focus.id);
    const mine = saved.find((s) => s.place.id === focus.id);
    setSelectedId(mine ? mine.id : POI + focus.id);
    setExpanded(false);
    setMapFocus({ key: `pick:${focus.id}`, position: focus.location });
  }

  const pois = useMemo(() => {
    const list = nearby.filter((p) => !savedPlaceIds.has(p.id) && (category === "ALL" || p.category === category));
    if (focus && !savedPlaceIds.has(focus.id) && !list.some((p) => p.id === focus.id)) list.push(focus);
    return list;
  }, [nearby, savedPlaceIds, category, focus]);

  const selectedSaved = saved.find((s) => s.id === selectedId) ?? null;
  const selectedPoi = selectedId?.startsWith(POI) ? (pois.find((p) => POI + p.id === selectedId) ?? null) : null;

  // 핀을 누르면 선택하고, 카드에 가리지 않도록 지도를 그 핀으로 옮깁니다 (축척은 그대로). 같은 핀을 다시 누르면 선택 해제
  const select = (id: string) => {
    if (id === selectedId) {
      setSelectedId(null);
      return;
    }
    setSelectedId(id);
    setExpanded(false);
    const place = id.startsWith(POI) ? pois.find((p) => POI + p.id === id) : saved.find((s) => s.id === id)?.place;
    if (place) setMapFocus({ key: `tap:${id}:${Date.now()}`, position: place.location, keepZoom: true });
  };

  return (
    <div className="relative">
      {/* 범례는 작은 안내 버튼으로 접어 지도를 덜 가리게 */}
      <div className="absolute top-3 left-3 z-10">
        <button
          type="button"
          onClick={() => setLegendOpen((v) => !v)}
          aria-expanded={legendOpen}
          aria-label="지도 표시 안내"
          className="inline-flex size-8 items-center justify-center rounded-full bg-white/95 text-ink-sub shadow-sm"
        >
          <Info size={16} />
        </button>
        {legendOpen && (
          <div className="mt-1.5 space-y-1 rounded-lg bg-white/95 px-3 py-2 text-[11px] text-ink shadow-sm">
            <p className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-full border border-white bg-primary" /> 내가 저장한 곳
            </p>
            <p className="flex items-center gap-1.5 text-ink-sub">
              <span className="size-2 rounded-full bg-ink-faint" /> 주변 장소
            </p>
          </div>
        )}
      </div>
      <MapView
        className="h-[calc(100dvh-250px)] min-h-[360px]"
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
        onMarkerClick={select}
        onBoundsChange={setBounds}
        focus={mapFocus}
        caption="예시 지도"
      />

      {selectedSaved && (
        <PlaceCard
          place={selectedSaved.place}
          expanded={expanded}
          onToggle={() => setExpanded((v) => !v)}
          badge={selectedSaved.visitStatus === "VISITED" ? <Badge tone="neutral">방문 완료</Badge> : null}
        >
          <p className="truncate text-[12px] text-ink-faint">{selectedSaved.source.label}</p>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <ButtonLink href={routes.newPlan([selectedSaved.place.id])} size="md">
              일정에 추가
            </ButtonLink>
            <Button size="md" variant="secondary" onClick={() => setAddOpen(true)}>
              직접 추가 / 링크
            </Button>
          </div>
          <Link href={routes.place(selectedSaved.place.id)} className="mt-2 block py-1 text-center text-[13px] font-bold text-primary-deep">
            장소 상세 보기 →
          </Link>
        </PlaceCard>
      )}

      {selectedPoi && (
        <PlaceCard
          place={selectedPoi}
          expanded={expanded}
          onToggle={() => setExpanded((v) => !v)}
          action={<SavePlaceButton place={selectedPoi} source={{ type: "SEARCH", refId: null, label: "내 지도에서 발견" }} />}
        >
          <p className="text-[12px] text-ink-sub">영업 {formatHours(selectedPoi.businessHours)}</p>
          <Link href={routes.place(selectedPoi.id)} className="mt-2 block py-1 text-center text-[13px] font-bold text-primary-deep">
            장소 상세 보기 →
          </Link>
        </PlaceCard>
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

/**
 * 장소 카드: 기본은 높이 80px 남짓의 요약(사진 · 이름 · 지역/카테고리), 누르면 펼쳐서 children을 보여 줍니다.
 * action은 요약 줄 오른쪽에 늘 보이는 버튼(찜콩 등)입니다.
 */
function PlaceCard({
  place,
  expanded,
  onToggle,
  badge,
  action,
  children,
}: {
  place: Place;
  expanded: boolean;
  onToggle: () => void;
  badge?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="absolute inset-x-3 bottom-3 z-10 rounded-card bg-bg p-3 shadow-[0_2px_16px_rgba(0,0,0,0.12)]">
      <div className="flex items-center gap-2">
        <button type="button" onClick={onToggle} aria-expanded={expanded} className="flex min-w-0 flex-1 items-center gap-3 text-left">
          <Thumb src={place.imageUrl} alt="" className="size-14" />
          <span className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <span className="truncate text-[15px] font-bold">{place.name}</span>
              {badge}
            </span>
            <span className="block truncate text-[12px] text-ink-sub">
              {place.region} · {categoryLabel(place.category)}
            </span>
          </span>
          <span className="sr-only">{expanded ? "접기" : "펼치기"}</span>
          {expanded ? (
            <ChevronDown size={18} className="shrink-0 text-ink-faint" aria-hidden />
          ) : (
            <ChevronUp size={18} className="shrink-0 text-ink-faint" aria-hidden />
          )}
        </button>
        {action}
      </div>
      {expanded && <div className="mt-2 border-t border-line pt-2">{children}</div>}
    </div>
  );
}
