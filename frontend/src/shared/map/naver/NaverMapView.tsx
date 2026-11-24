"use client";

import { Minus, Plus } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import type { LatLng, LatLngBounds, MapMarker } from "../types";
import { naverMapGlOptions, shouldUseNaverGl } from "./config";
import { clampNaverZoom, fromNaverBounds, fromNaverLatLng, metersPerPixelToZoom, toNaverLatLng } from "./convert";
import { loadNaverSdk } from "./loadNaverSdk";

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
  /** 화면에 보이는 범위가 바뀔 때 알림 (처음 맞춤 · 이동 · 확대/축소) */
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
  const markersRef = useRef<naver.maps.Marker[]>([]);
  const markerListenersRef = useRef<object[]>([]);
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

  const pins = markers.filter((m) => m.tone !== "poi");
  const pinCount = pins.length;

  // SDK 로드 + 지도 생성
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    let cancelled = false;
    const listeners: object[] = [];
    let ro: ResizeObserver | undefined;

    loadNaverSdk()
      .then(() => {
        if (cancelled || !containerRef.current) return;

        const center = initialCenter ?? DEFAULT_CENTER;
        const zoom =
          initialZoom !== undefined
            ? clampNaverZoom(initialZoom, USE_GL)
            : metersPerPixelToZoom(initialMpp ?? 4, USE_GL);
        const map = new naver.maps.Map(containerRef.current, {
          center: toNaverLatLng(center),
          zoom,
          scaleControl: false,
          mapDataControl: false,
          logoControl: true,
          zoomControl: false,
          ...naverMapGlOptions(),
        });
        mapRef.current = map;
        setStatus("ready");

        const emitBounds = () => {
          if (!mapRef.current) return;
          onBoundsChangeRef.current?.(fromNaverBounds(mapRef.current.getBounds()));
        };
        const emitCamera = () => {
          if (!mapRef.current) return;
          onCameraChangeRef.current?.({
            center: fromNaverLatLng(mapRef.current.getCenter()),
            zoom: mapRef.current.getZoom(),
          });
        };
        const emitCenter = () => {
          if (!mapRef.current || suppressMoveRef.current) return;
          onCenterChangeRef.current?.(fromNaverLatLng(mapRef.current.getCenter()));
        };
        const onIdle = () => {
          emitBounds();
          emitCamera();
          suppressMoveRef.current = false;
        };

        listeners.push(naver.maps.Event.addListener(map, "idle", onIdle));
        listeners.push(naver.maps.Event.addListener(map, "center_changed", emitCenter));
        listeners.push(naver.maps.Event.addListener(map, "zoom_changed", emitBounds));
        listeners.push(naver.maps.Event.addListener(map, "dragstart", () => onDragStartRef.current?.()));
        // 마커 클릭은 Marker 이벤트로만 가고, 여기 click은 지도 빈 곳
        listeners.push(naver.maps.Event.addListener(map, "click", () => onMapClickRef.current?.()));

        emitBounds();
        emitCamera();

        ro = new ResizeObserver(() => {
          map.autoResize();
          emitBounds();
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
      for (const listener of listeners) naver.maps.Event.removeListener(listener);
      ro?.disconnect();
      clearMarkers(markersRef, markerListenersRef);
      userLocationMarkerRef.current?.setMap(null);
      userLocationMarkerRef.current = null;
      polylineRef.current?.setMap(null);
      polylineRef.current = null;
      mapRef.current?.destroy();
      mapRef.current = null;
    };
    // 최초 마운트 시 한 번만 생성 (initialCenter/mpp/zoom은 첫 값만 사용)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  // 마커 동기화
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;

    clearMarkers(markersRef, markerListenersRef);

    for (const m of markers) {
      const selected = m.id === selectedId;
      const content = createMarkerElement(m, selected, pinCount);
      const size = estimateMarkerSize(m, selected, pinCount);
      const marker = new naver.maps.Marker({
        map,
        position: toNaverLatLng(m.position),
        icon: {
          content,
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
      markersRef.current.push(marker);
      markerListenersRef.current.push(listener);
    }
  }, [markers, selectedId, pinCount, status]);

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
      <div ref={containerRef} className="absolute inset-0 size-full" />

      {status === "loading" && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-map-land text-[13px] text-ink-sub">
          지도를 불러오는 중…
        </div>
      )}
      {status === "error" && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-map-land px-6 text-center text-[13px] text-ink-sub">
          {errorMessage}
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

function clearMarkers(
  markersRef: { current: naver.maps.Marker[] },
  listenersRef: { current: object[] },
) {
  for (const listener of listenersRef.current) naver.maps.Event.removeListener(listener);
  listenersRef.current = [];
  for (const marker of markersRef.current) marker.setMap(null);
  markersRef.current = [];
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

function estimateMarkerSize(m: MapMarker, selected: boolean, pinCount: number): { w: number; h: number } {
  const showTitle = !!m.title && (m.tone === "poi" ? selected : selected || pinCount <= 6);
  if (m.tone === "poi") {
    return { w: 28, h: showTitle ? 36 : 20 };
  }
  const pin = selected ? 36 : 28;
  return { w: Math.max(pin, showTitle ? 96 : pin), h: pin + (showTitle ? 16 : 0) };
}

function createMarkerElement(m: MapMarker, selected: boolean, pinCount: number): HTMLElement {
  const wrap = document.createElement("div");
  wrap.style.cssText =
    "display:flex;flex-direction:column;align-items:center;pointer-events:auto;cursor:pointer;";

  const primary = cssVar("--color-primary", "#86cb16");
  const primaryDeep = cssVar("--color-primary-deep", "#517c26");
  const onPrimary = cssVar("--color-on-primary", "#1a2e05");
  const ink = cssVar("--color-ink", "#20251f");
  const inkFaint = cssVar("--color-ink-faint", "#9aa093");

  if (m.tone === "poi") {
    const dot = document.createElement("span");
    const size = selected ? 16 : 12;
    const bg = selected ? ink : inkFaint;
    dot.style.cssText = `display:block;width:${size}px;height:${size}px;border-radius:9999px;border:2px solid #fff;background:${bg};box-shadow:0 1px 3px rgba(0,0,0,0.3);`;
    wrap.appendChild(dot);
    if (m.title && selected) wrap.appendChild(titleEl(m.title, ink));
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

  if (m.title && (selected || pinCount <= 6)) wrap.appendChild(titleEl(m.title, ink));
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
