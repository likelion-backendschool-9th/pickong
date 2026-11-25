"use client";

import { Minus, Plus } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import type { LatLng, LatLngBounds, MapMarker } from "../types";
import { naverMapGlOptions, shouldUseNaverGl } from "./config";
import { clampNaverZoom, fromNaverBounds, metersPerPixelToZoom, toNaverLatLng } from "./convert";
import { loadNaverSdk } from "./loadNaverSdk";
import { diffMarkers, planMarkers, type MarkerPlan } from "./markerDiff";

const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };
const USE_GL = shouldUseNaverGl();

export interface NaverMapViewProps {
  markers?: MapMarker[];
  selectedId?: string | null;
  onMarkerClick?: (id: string) => void;
  /** 마커가 아닌 지도 빈 곳을 눌렀을 때 */
  onMapClick?: () => void;
  /** 처음 중심. 없으면 기본 지역(성수) */
  center?: LatLng;
  /** 처음 축척 (m/px). center와 함께 사용 */
  metersPerPixel?: number;
  /** 처음 네이버 줌 레벨. 있으면 metersPerPixel보다 우선 */
  zoom?: number;
  /** 마커 순서대로 잇는 경로 */
  path?: LatLng[];
  showZoom?: boolean;
  /** 지도 이동 시 중심 좌표 알림 (좌표 직접 지정용) */
  onCenterChange?: (center: LatLng) => void;
  /** 화면에 보이는 범위가 안정됐을 때 알림 (최초 · idle · 리사이즈 안정화). 드래그/줌 중간에는 호출하지 않습니다. */
  onBoundsChange?: (bounds: LatLngBounds) => void;
  /** idle 시 중심·줌 (탭 복귀용 카메라 저장) */
  onCameraChange?: (camera: { center: LatLng; zoom: number }) => void;
  /** 사용자가 지도를 드래그하기 시작했을 때 (초기 GPS 적용 취소 등) */
  onDragStart?: () => void;
  /** 이 위치로 지도를 옮깁니다 (검색 결과 선택 등). key가 바뀔 때마다 한 번 이동 */
  focus?: { key: string; position: LatLng; keepZoom?: boolean } | null;
  /** 중앙 고정 핀 표시 (좌표 직접 지정용) */
  crosshair?: boolean;
  /** GPS로 확인한 내 위치 (파란 점). 장소 마커와 별도 */
  userLocation?: LatLng | null;
  caption?: string;
  className?: string;
  children?: ReactNode;
  ariaLabel?: string;
}

/**
 * 네이버 지도 SDK 구현. 화면은 `@/shared/map`의 MapView만 쓰고, 이 컴포넌트는 shared/map 안에서만 사용합니다.
 */
export function NaverMapView({
  markers = [],
  selectedId,
  onMarkerClick,
  onMapClick,
  center: initialCenter,
  metersPerPixel: initialMpp,
  zoom: initialZoom,
  path,
  showZoom = true,
  onCenterChange,
  onBoundsChange,
  onCameraChange,
  onDragStart,
  focus,
  crosshair,
  userLocation,
  caption,
  className,
  children,
  ariaLabel = "지도",
}: NaverMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<naver.maps.Map | null>(null);
  const markersRef = useRef<Map<string, DrawnMarker>>(new Map());
  const userLocationMarkerRef = useRef<naver.maps.Marker | null>(null);
  const polylineRef = useRef<naver.maps.Polyline | null>(null);
  const focusedKeyRef = useRef<string | null>(null);
  const suppressMoveRef = useRef(false);

  const onCenterChangeRef = useRef(onCenterChange);
  const onBoundsChangeRef = useRef(onBoundsChange);
  const onCameraChangeRef = useRef(onCameraChange);
  const onDragStartRef = useRef(onDragStart);
  const onMarkerClickRef = useRef(onMarkerClick);
  const onMapClickRef = useRef(onMapClick);
  useEffect(() => {
    onCenterChangeRef.current = onCenterChange;
    onBoundsChangeRef.current = onBoundsChange;
    onCameraChangeRef.current = onCameraChange;
    onDragStartRef.current = onDragStart;
    onMarkerClickRef.current = onMarkerClick;
    onMapClickRef.current = onMapClick;
  });

  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  /** 로딩 실패 후 다시 시도할 때 effect를 재실행합니다. */
  const [bootId, setBootId] = useState(0);
  /**
   * 지도를 새로 만들 때마다 늘어납니다.
   * 탭을 떠났다 돌아오면 지도는 새로 생기지만 status는 ready 그대로라, 이 값으로 마커를 다시 그립니다.
   */
  const [mapVersion, setMapVersion] = useState(0);

  const retryLoad = () => {
    setErrorMessage(null);
    setStatus("loading");
    setBootId((n) => n + 1);
  };

  // SDK 로드 + 지도 생성
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    let cancelled = false;
    let mapCreated = false;
    const listeners: object[] = [];
    let ro: ResizeObserver | undefined;
    let resizeEmitTimer: ReturnType<typeof setTimeout> | undefined;
    /**
     * GL에서 idle 직후 getCenter()가 한 박자 늦은 중심을 주는 경우가 있습니다.
     * center_changed 이벤트 인자로만 스냅샷을 갱신하고, 세션 저장은 그 스냅샷을 씁니다.
     * (매 이벤트마다 sessionStorage에 쓰면 탭 이탈 teardown이 좌표를 덮어씁니다.)
     */
    let lastCamera: { center: LatLng; zoom: number } = {
      center: initialCenter ?? DEFAULT_CENTER,
      zoom:
        initialZoom !== undefined
          ? clampNaverZoom(initialZoom, USE_GL)
          : metersPerPixelToZoom(initialMpp ?? 4, USE_GL),
    };

    loadNaverSdk()
      .then(() => {
        if (cancelled || !containerRef.current) return;

        const map = new naver.maps.Map(containerRef.current, {
          center: toNaverLatLng(lastCamera.center),
          zoom: lastCamera.zoom,
          scaleControl: false,
          mapDataControl: false,
          logoControl: true,
          zoomControl: false,
          ...naverMapGlOptions(),
        });
        mapRef.current = map;
        mapCreated = true;
        setStatus("ready");
        setMapVersion((n) => n + 1);

        const emitBounds = () => {
          if (!mapRef.current) return;
          onBoundsChangeRef.current?.(fromNaverBounds(mapRef.current.getBounds()));
        };
        const persistLastCamera = () => {
          onCameraChangeRef.current?.(lastCamera);
        };
        const noteCenterFromEvent = (value: unknown) => {
          const snapped = latLngFromNaverCenter(value);
          if (!snapped) return;
          lastCamera = {
            center: snapped,
            zoom: mapRef.current?.getZoom() ?? lastCamera.zoom,
          };
        };
        /**
         * 장소 조회는 이동·줌이 끝난 idle에만 bounds를 올립니다.
         * zoom_changed/드래그 중간마다 올리면 bbox API가 불필요하게 반복됩니다.
         */
        const onIdle = () => {
          emitBounds();
          if (mapRef.current) {
            lastCamera = { ...lastCamera, zoom: mapRef.current.getZoom() };
          }
          persistLastCamera();
          suppressMoveRef.current = false;
        };

        listeners.push(naver.maps.Event.addListener(map, "idle", onIdle));
        listeners.push(
          naver.maps.Event.addListener(map, "center_changed", (nextCenter: unknown) => {
            noteCenterFromEvent(nextCenter);
            if (!suppressMoveRef.current) {
              onCenterChangeRef.current?.(lastCamera.center);
            }
          }),
        );
        listeners.push(
          naver.maps.Event.addListener(map, "zoom_changed", () => {
            if (mapRef.current) {
              lastCamera = { ...lastCamera, zoom: mapRef.current.getZoom() };
            }
          }),
        );
        listeners.push(naver.maps.Event.addListener(map, "dragstart", () => onDragStartRef.current?.()));
        // 마커 클릭은 Marker 이벤트로만 가고, 여기 click은 지도 빈 곳
        listeners.push(naver.maps.Event.addListener(map, "click", () => onMapClickRef.current?.()));

        emitBounds();
        // 생성 직후 persist는 Activity 복귀 시 낡은 initialCenter로 세션을 덮어쓸 수 있어
        // idle·teardown에서만 저장합니다.

        ro = new ResizeObserver(() => {
          map.autoResize();
          // 리사이즈 중 연속 emit을 막고, 레이아웃이 안정된 뒤 한 번만 알립니다.
          clearTimeout(resizeEmitTimer);
          resizeEmitTimer = setTimeout(emitBounds, 120);
        });
        ro.observe(containerRef.current);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setStatus("error");
        setErrorMessage(err instanceof Error ? err.message : "지도를 불러오지 못했어요.");
      });

    return () => {
      cancelled = true;
      // teardown resize가 lastCamera를 건드리지 않도록 RO·리스너를 먼저 끊고 스냅샷만 저장
      clearTimeout(resizeEmitTimer);
      ro?.disconnect();
      const saved = lastCamera;
      for (const listener of listeners) naver.maps.Event.removeListener(listener);
      if (mapCreated) onCameraChangeRef.current?.(saved);
      clearMarkers(markersRef);
      userLocationMarkerRef.current?.setMap(null);
      userLocationMarkerRef.current = null;
      polylineRef.current?.setMap(null);
      polylineRef.current = null;
      mapRef.current?.destroy();
      mapRef.current = null;
    };
    // bootId가 바뀔 때(재시도)만 다시 생성. initialCenter/mpp/zoom은 첫 값만 사용합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bootId]);

  // focus 이동 (지도가 아직 없으면 status가 ready가 될 때 다시 적용)
  // panTo + setZoom을 연속 호출하면 애니메이션이 겹쳐 줌만 되고 중심 이동이 빠질 수 있어,
  // 줌까지 바꿀 때는 morph로 한 번에 맞춥니다.
  useEffect(() => {
    const map = mapRef.current;
    if (status !== "ready" || !map || !focus || focus.key === focusedKeyRef.current) return;
    focusedKeyRef.current = focus.key;
    suppressMoveRef.current = true;
    const target = toNaverLatLng(focus.position);
    if (focus.keepZoom) {
      map.panTo(target);
      return;
    }
    const next = Math.max(map.getZoom(), metersPerPixelToZoom(2.6, USE_GL));
    map.morph(target, next);
  }, [focus, status]);

  // 마커 동기화: 모습이 바뀐 마커만 지우고 다시 그립니다.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;

    const drawn = markersRef.current;
    const { remove, add } = diffMarkers(drawn, planMarkers(markers, selectedId));

    for (const key of remove) {
      const old = drawn.get(key);
      if (!old) continue;
      removeMarker(old);
      drawn.delete(key);
    }
    if (add.length === 0) return;

    // 색상은 모든 마커가 같으므로 한 번만 읽습니다.
    const colors = readMarkerColors();
    for (const plan of add) {
      const { marker: m, selected } = plan;
      const size = estimateMarkerSize(plan);
      const marker = new naver.maps.Marker({
        map,
        position: toNaverLatLng(m.position),
        icon: {
          content: createMarkerElement(plan, colors),
          size: new naver.maps.Size(size.w, size.h),
          anchor: new naver.maps.Point(size.w / 2, m.tone === "poi" ? size.h / 2 : size.h),
        },
        title: m.title,
        zIndex: selected ? 3 : m.tone === "poi" ? 1 : 2,
        clickable: true,
      });
      const listener = naver.maps.Event.addListener(marker, "click", () => {
        onMarkerClickRef.current?.(m.id);
      });
      drawn.set(plan.key, { marker, listener, signature: plan.signature });
    }
  }, [markers, selectedId, status, mapVersion]);

  // 내 위치 (GPS)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;

    if (!userLocation) {
      userLocationMarkerRef.current?.setMap(null);
      userLocationMarkerRef.current = null;
      return;
    }

    const position = toNaverLatLng(userLocation);
    if (userLocationMarkerRef.current) {
      userLocationMarkerRef.current.setPosition(position);
      userLocationMarkerRef.current.setMap(map);
      return;
    }

    const size = 20;
    userLocationMarkerRef.current = new naver.maps.Marker({
      map,
      position,
      icon: {
        content: createUserLocationElement(),
        size: new naver.maps.Size(size, size),
        anchor: new naver.maps.Point(size / 2, size / 2),
      },
      title: "내 위치",
      zIndex: 4,
      clickable: false,
    });
  }, [userLocation, status]);

  // 경로선
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;

    if (!path || path.length < 2) {
      polylineRef.current?.setMap(null);
      polylineRef.current = null;
      return;
    }

    const strokeColor =
      getComputedStyle(document.documentElement).getPropertyValue("--color-primary-deep").trim() || "#517c26";
    const naverPath = path.map(toNaverLatLng);

    if (polylineRef.current) {
      polylineRef.current.setPath(naverPath);
      polylineRef.current.setMap(map);
      return;
    }

    polylineRef.current = new naver.maps.Polyline({
      map,
      path: naverPath,
      strokeWeight: 3,
      strokeColor,
      strokeOpacity: 0.9,
      strokeStyle: "shortdash",
      zIndex: 0,
    });
  }, [path, status]);

  const zoomBy = (delta: number) => {
    const map = mapRef.current;
    if (!map) return;
    map.setZoom(clampNaverZoom(map.getZoom() + delta, USE_GL), true);
  };

  return (
    <div
      role="application"
      aria-label={ariaLabel}
      className={cn("relative overflow-hidden bg-map-land select-none", className)}
    >
      {/*
        SDK가 지도 요소에 인라인 position: relative를 넣어 absolute가 무효가 됩니다.
        래퍼가 높이를 확정해야 부모가 min-h · flex-1일 때도 지도 높이가 0이 되지 않습니다.
      */}
      <div className="absolute inset-0">
        <div ref={containerRef} className="size-full" />
      </div>

      {status === "loading" && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-map-land text-[13px] text-ink-sub">
          지도를 불러오는 중…
        </div>
      )}
      {status === "error" && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-map-land px-6 text-center">
          <p className="text-[13px] text-ink-sub">{errorMessage}</p>
          <button
            type="button"
            onClick={retryLoad}
            className="rounded-lg border border-line bg-white px-3 py-1.5 text-[13px] font-bold text-ink shadow-sm active:bg-fill"
          >
            다시 시도
          </button>
        </div>
      )}

      {crosshair && (
        <div className="pointer-events-none absolute top-1/2 left-1/2 z-10 -translate-x-1/2 -translate-y-full" aria-hidden>
          <svg width="34" height="44" viewBox="0 0 34 44">
            <path
              d="M17 43s14-14.6 14-25.5C31 8.9 24.7 2.5 17 2.5S3 8.9 3 17.5C3 28.4 17 43 17 43z"
              fill="var(--color-primary)"
              stroke="white"
              strokeWidth="2.5"
            />
            <circle cx="17" cy="17" r="5" fill="var(--color-on-primary)" />
          </svg>
        </div>
      )}

      {showZoom && status === "ready" && (
        <div className="absolute right-3 bottom-8 z-10 flex flex-col overflow-hidden rounded-lg border border-line bg-white shadow-sm">
          <button type="button" aria-label="확대" onClick={() => zoomBy(1)} className="flex size-9 items-center justify-center active:bg-fill">
            <Plus size={18} />
          </button>
          <span className="h-px bg-line" />
          <button type="button" aria-label="축소" onClick={() => zoomBy(-1)} className="flex size-9 items-center justify-center active:bg-fill">
            <Minus size={18} />
          </button>
        </div>
      )}

      {caption && <span className="pointer-events-none absolute bottom-2 left-3 z-10 text-[10px] text-ink-faint">{caption}</span>}
      {children}
    </div>
  );
}

/** center_changed 인자가 LatLng인지 확인한 뒤 { lat, lng }로 변환합니다. */
function latLngFromNaverCenter(value: unknown): LatLng | null {
  if (!value || typeof value !== "object") return null;
  const latFn = (value as { lat?: unknown }).lat;
  const lngFn = (value as { lng?: unknown }).lng;
  if (typeof latFn !== "function" || typeof lngFn !== "function") return null;
  const lat = (latFn as () => unknown).call(value);
  const lng = (lngFn as () => unknown).call(value);
  if (typeof lat !== "number" || typeof lng !== "number") return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return { lat, lng };
}

interface DrawnMarker {
  marker: naver.maps.Marker;
  listener: object;
  signature: string;
}

function removeMarker({ marker, listener }: DrawnMarker) {
  naver.maps.Event.removeListener(listener);
  marker.setMap(null);
}

function clearMarkers(drawnRef: { current: Map<string, DrawnMarker> }) {
  for (const item of drawnRef.current.values()) removeMarker(item);
  drawnRef.current.clear();
}

function createUserLocationElement(): HTMLElement {
  const wrap = document.createElement("div");
  wrap.setAttribute("aria-label", "내 위치");
  wrap.className = "relative flex size-5 items-center justify-center";

  const pulse = document.createElement("span");
  pulse.className = "absolute size-5 rounded-full bg-map-me animate-map-me-pulse";
  pulse.setAttribute("aria-hidden", "true");

  const dot = document.createElement("span");
  dot.className =
    "relative size-3.5 rounded-full border-2 border-white bg-map-me shadow-[0_1px_4px_rgba(0,0,0,0.3)]";

  wrap.append(pulse, dot);
  return wrap;
}

function estimateMarkerSize({ marker: m, selected, showTitle }: MarkerPlan): { w: number; h: number } {
  if (m.tone === "poi") {
    return { w: 28, h: showTitle ? 36 : 20 };
  }
  const pin = selected ? 36 : 28;
  return { w: Math.max(pin, showTitle ? 96 : pin), h: pin + (showTitle ? 16 : 0) };
}

interface MarkerColors {
  primary: string;
  primaryDeep: string;
  onPrimary: string;
  ink: string;
  inkFaint: string;
}

function readMarkerColors(): MarkerColors {
  return {
    primary: cssVar("--color-primary", "#86cb16"),
    primaryDeep: cssVar("--color-primary-deep", "#517c26"),
    onPrimary: cssVar("--color-on-primary", "#1a2e05"),
    ink: cssVar("--color-ink", "#20251f"),
    inkFaint: cssVar("--color-ink-faint", "#9aa093"),
  };
}

function createMarkerElement({ marker: m, selected, showTitle }: MarkerPlan, colors: MarkerColors): HTMLElement {
  const { primary, primaryDeep, onPrimary, ink, inkFaint } = colors;
  const wrap = document.createElement("div");
  wrap.style.cssText =
    "display:flex;flex-direction:column;align-items:center;pointer-events:auto;cursor:pointer;";

  if (m.tone === "poi") {
    const dot = document.createElement("span");
    const size = selected ? 16 : 12;
    const bg = selected ? ink : inkFaint;
    dot.style.cssText = `display:block;width:${size}px;height:${size}px;border-radius:9999px;border:2px solid #fff;background:${bg};box-shadow:0 1px 3px rgba(0,0,0,0.3);`;
    wrap.appendChild(dot);
    if (m.title && showTitle) wrap.appendChild(titleEl(m.title, ink));
    return wrap;
  }

  const pin = document.createElement("span");
  const size = selected ? 36 : 28;
  let bg = primary;
  let color = onPrimary;
  if (m.tone === "muted") {
    bg = "#ffffff";
    color = ink;
  } else if (m.tone === "fixed") {
    bg = primaryDeep;
    color = "#ffffff";
  }
  pin.style.cssText = [
    "display:flex;align-items:center;justify-content:center;",
    `width:${size}px;height:${size}px;border-radius:9999px;`,
    `border:2px solid #fff;background:${bg};color:${color};`,
    "font-size:12px;font-weight:700;box-shadow:0 1px 4px rgba(0,0,0,0.25);",
    selected ? `outline:3px solid color-mix(in srgb, ${primaryDeep} 60%, transparent);` : "",
  ].join("");

  if (m.tone === "fixed" && !m.label) {
    pin.innerHTML =
      '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>';
  } else if (m.label) {
    pin.textContent = m.label;
  }
  wrap.appendChild(pin);

  if (m.title && showTitle) wrap.appendChild(titleEl(m.title, ink));
  return wrap;
}

function titleEl(text: string, color: string): HTMLElement {
  const el = document.createElement("span");
  el.textContent = text;
  el.style.cssText = `margin-top:2px;max-width:96px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border-radius:4px;background:rgba(255,255,255,0.9);padding:0 4px;font-size:10px;font-weight:700;color:${color};`;
  return el;
}

function cssVar(name: string, fallback: string): string {
  if (typeof document === "undefined") return fallback;
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}
