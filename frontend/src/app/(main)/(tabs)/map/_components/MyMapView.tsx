"use client";

import { ChevronDown, ChevronUp, Info, Link2, LocateFixed, MapPinPlus } from "lucide-react";
import Link from "next/link";
import { useEffect, useLayoutEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import {
  categoryLabel,
  formatHours,
  isStackedCluster,
  usePlacesInBounds,
  type Place,
  type PlaceCategory,
  type PlaceCluster,
  type SavedPlace,
} from "@/entities/place";
import { SavePlaceButton } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { getCurrentPosition, type LatLng, type LatLngBounds } from "@/shared/lib/geo";
import { MapView, type MapMarker } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Badge, Skeleton, Thumb } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";
import { PlaceClusterSheet } from "../../../_components/PlaceClusterSheet";
import { findCluster, toClusterMarker } from "../../../_lib/clusterMarker";
import {
  DEFAULT_MAP_ZOOM,
  hasBootstrappedMapGps,
  getMapSessionSnapshot,
  getServerMapSessionSnapshot,
  markMapGpsBootstrapped,
  subscribeMapSession,
  writeMapCamera,
  writeUserLocation,
  type MapCamera,
  type MapSessionState,
} from "../../../_lib/mapSession";

const POI = "poi:";

type FocusRequest = { key: string; position: LatLng; keepZoom?: boolean; bounds?: LatLngBounds } | null;

/**
 * 내 지도 (지도 우선형): 주변 장소는 작은 점(많으면 숫자 원으로 묶음), 내가 저장한 곳은 초록 핀.
 * 세션 최초 진입 때만 GPS로 중심을 잡고, 이후 재진입은 마지막 카메라(중심·줌)를 복원합니다.
 * 장소를 누르면 지도가 그 핀으로 옮겨지고(카드에 가리지 않게), 아래에 작은 요약 카드가 뜹니다.
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
  const toast = useToast();
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const { data: nearby } = usePlacesInBounds(bounds, category);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  /** 고른 주변 장소. 지도를 옮기거나 축소해 묶음에 들어가도 선택을 유지합니다. */
  const [pickedPoi, setPickedPoi] = useState<Place | null>(null);
  /** 목록으로 열어 둔 묶음 (확대해도 나뉘지 않는 묶음) */
  const [openCluster, setOpenCluster] = useState<PlaceCluster | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [legendOpen, setLegendOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [mapFocus, setMapFocus] = useState<FocusRequest>(null);
  const [locating, setLocating] = useState(false);
  /**
   * 클라이언트에서 세션을 읽은 뒤에만 지도를 만듭니다.
   * (SSR hydrate 때 useState(sessionStorage)는 항상 null로 고정되는 문제를 피합니다.)
   */
  const snapshot = useSyncExternalStore(subscribeMapSession, getMapSessionSnapshot, getServerMapSessionSnapshot);
  const session = useMemo(() => snapshot ? JSON.parse(snapshot) as MapSessionState : null, [snapshot]);
  const bootCamera = session?.camera ?? null;
  const userLocation = session?.userLocation ?? null;
  const bootReady = session?.bootstrapped ?? false;
  /** 최초 GPS 실패 메시지. 있으면 지도 위에 재시도 UI를 띄웁니다. */
  const [gpsBootError, setGpsBootError] = useState<string | null>(null);
  const [gpsBootRetrying, setGpsBootRetrying] = useState(false);

  const applyGpsPosition = (position: LatLng, focusKey: string) => {
    const camera: MapCamera = { center: position, zoom: DEFAULT_MAP_ZOOM };
    writeMapCamera(camera);
    writeUserLocation(position);
    setMapFocus({ key: focusKey, position });
    setGpsBootError(null);
  };

  /**
   * cacheComponents(Activity) 복귀 시 외부 저장소 구독이 최신 세션을 다시 읽습니다.
   * 별도의 복사 상태를 유지하지 않아 낡은 카메라 값으로 세션을 덮어쓰지 않습니다.
   */

  // 세션 최초 진입만 GPS → 카메라 확정 후에 MapView를 마운트합니다.
  useEffect(() => {
    if (hasBootstrappedMapGps()) return;

    let cancelled = false;

    async function boot() {
      try {
        const position = await getCurrentPosition();
        if (cancelled) return;
        // Strict Mode 재마운트에도 살아남도록 세션에 먼저 남깁니다.
        const camera: MapCamera = { center: position, zoom: DEFAULT_MAP_ZOOM };
        writeMapCamera(camera);
        writeUserLocation(position);
      } catch (err) {
        if (cancelled) return;
        // 권한 거부·실패 → MapView 기본 지역 + 재시도 안내.
        setGpsBootError(err instanceof Error ? err.message : "위치를 불러오지 못했어요.");
      } finally {
        if (!cancelled) {
          markMapGpsBootstrapped();
        }
      }
    }

    void boot();
    return () => {
      cancelled = true;
    };
  }, []);

  /** 팬·줌 결과를 저장하면 외부 저장소 구독을 통해 지도 props도 갱신됩니다. */
  const persistCamera = (camera: MapCamera) => {
    writeMapCamera(camera);
  };

  /** 최초 GPS가 실패한 뒤, 같은 화면에서 위치를 다시 요청합니다. */
  const retryGpsBoot = async () => {
    if (gpsBootRetrying) return;
    setGpsBootRetrying(true);
    try {
      const position = await getCurrentPosition();
      applyGpsPosition(position, `gps-retry:${position.lat},${position.lng}:${Date.now()}`);
    } catch (err) {
      setGpsBootError(err instanceof Error ? err.message : "위치를 불러오지 못했어요.");
    } finally {
      setGpsBootRetrying(false);
    }
  };

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
    const matches = (p: Place) => !savedPlaceIds.has(p.id) && (category === "ALL" || p.category === category);
    const list = nearby.places.filter(matches);
    const has = (p: Place) => list.some((x) => x.id === p.id);
    if (pickedPoi && matches(pickedPoi) && !has(pickedPoi)) list.push(pickedPoi);
    if (focus && !savedPlaceIds.has(focus.id) && !has(focus)) list.push(focus);
    return list;
  }, [nearby.places, savedPlaceIds, category, pickedPoi, focus]);

  // 렌더링마다 배열이 새로 생기면 지도가 매번 모든 마커를 비교하므로, 내용이 바뀔 때만 만듭니다.
  const markers = useMemo<MapMarker[]>(
    () => [
      ...nearby.clusters.map(toClusterMarker),
      ...pois.map((p) => ({ id: POI + p.id, position: p.location, title: p.name, tone: "poi" as const })),
      ...saved.map((s, i) => ({
        id: s.id,
        position: s.place.location,
        label: String(i + 1),
        title: s.place.name,
        tone: s.visitStatus === "VISITED" ? ("muted" as const) : ("primary" as const),
      })),
    ],
    [nearby.clusters, pois, saved],
  );

  const selectedSaved = saved.find((s) => s.id === selectedId) ?? null;
  const selectedPoi = selectedId?.startsWith(POI) ? (pois.find((p) => POI + p.id === selectedId) ?? null) : null;

  /** 지도 빈 곳을 누르면 하단 장소 카드를 닫습니다. */
  const clearSelection = () => {
    setSelectedId(null);
    setPickedPoi(null);
    setExpanded(false);
  };

  // 핀을 누르면 선택하고, 카드에 가리지 않도록 지도를 그 핀으로 옮깁니다 (축척은 그대로). 같은 핀을 다시 누르면 선택 해제
  // 숫자 원(묶음)을 누르면 그 범위로 확대하고, 확대해도 나뉘지 않으면 목록을 엽니다.
  const select = (id: string) => {
    const cluster = findCluster(nearby.clusters, id);
    if (cluster) {
      clearSelection();
      if (isStackedCluster(cluster)) setOpenCluster(cluster);
      else setMapFocus({ key: `cluster:${cluster.id}:${Date.now()}`, position: cluster.location, bounds: cluster.bounds });
      return;
    }
    if (id === selectedId) {
      clearSelection();
      return;
    }
    setSelectedId(id);
    setExpanded(false);
    const poi = pois.find((p) => POI + p.id === id) ?? null;
    setPickedPoi(poi);
    const place = poi ?? saved.find((s) => s.id === id)?.place;
    if (place) setMapFocus({ key: `tap:${id}:${Date.now()}`, position: place.location, keepZoom: true });
  };

  /** 묶음 목록에서 장소를 고릅니다. 저장한 장소면 그 핀을 선택합니다. */
  const selectFromCluster = (place: Place) => {
    const mine = saved.find((s) => s.place.id === place.id);
    setOpenCluster(null);
    setSelectedId(mine ? mine.id : POI + place.id);
    setPickedPoi(mine ? null : place);
    setExpanded(false);
    setMapFocus({ key: `tap:${place.id}:${Date.now()}`, position: place.location, keepZoom: true });
  };

  /** 버튼을 누를 때마다 GPS를 읽고 지도 중심을 그곳으로 옮깁니다. 조회 중에는 중복 클릭을 막습니다. */
  const goToMyLocation = async () => {
    if (locating) return;
    setLocating(true);
    try {
      const position = await getCurrentPosition();
      writeUserLocation(position);
      setMapFocus({ key: `me:${position.lat},${position.lng}:${Date.now()}`, position });
    } catch (err) {
      toast({
        message: "위치를 찾지 못했어요",
        detail: err instanceof Error ? err.message : undefined,
      });
    } finally {
      setLocating(false);
    }
  };

  if (!bootReady) {
    return <Skeleton className="h-[calc(100dvh-250px)] min-h-[360px] rounded-none" />;
  }

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
            <p className="flex items-center gap-1.5 text-ink-sub">
              <span className="inline-flex size-4 items-center justify-center rounded-full bg-ink text-[9px] font-bold text-bg">9</span>
              모여 있는 장소 수
            </p>
            <p className="flex items-center gap-1.5 text-ink-sub">
              <span className="size-2.5 rounded-full border border-white bg-map-me" /> 내 위치
            </p>
          </div>
        )}
      </div>
      <MapView
        className="h-[calc(100dvh-250px)] min-h-[360px]"
        center={bootCamera?.center}
        zoom={bootCamera?.zoom}
        markers={markers}
        selectedId={selectedId}
        onMarkerClick={select}
        onMapClick={clearSelection}
        onBoundsChange={setBounds}
        onCameraChange={persistCamera}
        focus={mapFocus}
        userLocation={userLocation}
        caption={undefined}
      >
        {gpsBootError && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-3 bg-map-land/95 px-6 text-center">
            <p className="text-[13px] text-ink-sub">{gpsBootError}</p>
            <div className="flex flex-col items-stretch gap-2 sm:flex-row sm:items-center">
              <Button type="button" size="sm" onClick={retryGpsBoot} disabled={gpsBootRetrying} aria-busy={gpsBootRetrying}>
                다시 시도
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={() => setGpsBootError(null)} disabled={gpsBootRetrying}>
                기본 지역으로 보기
              </Button>
            </div>
          </div>
        )}
        <button
          type="button"
          onClick={goToMyLocation}
          disabled={locating || !!gpsBootError}
          aria-label="내 위치로 이동"
          aria-busy={locating}
          className={cn(
            "absolute right-3 bottom-28 z-10 inline-flex size-9 items-center justify-center rounded-lg border border-line bg-white text-ink shadow-sm active:bg-fill disabled:pointer-events-none disabled:opacity-60",
            userLocation && "text-map-me",
          )}
        >
          <LocateFixed size={18} className={cn(locating && "animate-pulse")} />
        </button>
      </MapView>

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

      <PlaceClusterSheet
        cluster={openCluster}
        category={category}
        onClose={() => setOpenCluster(null)}
        onSelect={selectFromCluster}
      />

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
