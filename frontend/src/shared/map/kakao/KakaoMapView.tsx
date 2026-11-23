"use client";

import { Minus, Plus } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import type { LatLng, LatLngBounds, MapMarker } from "../types";
import { fromKakaoBounds, fromKakaoLatLng, metersPerPixelToLevel, toKakaoLatLng } from "./convert";
import { loadKakaoSdk } from "./loadKakaoSdk";

const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };
const FIT_PADDING = 56;
const FIT_MAX_LEVEL = 7;

export interface KakaoMapViewProps {
  markers?: MapMarker[];
  selectedId?: string | null;
  onMarkerClick?: (id: string) => void;
  /** 처음 중심. 없으면 마커 전체가 보이도록 맞춤 */
  center?: LatLng;
  /** 처음 축척 (m/px). center와 함께 사용 */
  metersPerPixel?: number;
  /** 마커 순서대로 잇는 경로 */
  path?: LatLng[];
  showZoom?: boolean;
  /** 지도 이동 시 중심 좌표 알림 (좌표 직접 지정용) */
  onCenterChange?: (center: LatLng) => void;
  /** 화면에 보이는 범위가 바뀔 때 알림 (처음 맞춤 · 이동 · 확대/축소) */
  onBoundsChange?: (bounds: LatLngBounds) => void;
  /** 이 위치로 지도를 옮깁니다 (검색 결과 선택 등). key가 바뀔 때마다 한 번 이동 */
  focus?: { key: string; position: LatLng; keepZoom?: boolean } | null;
  /** 중앙 고정 핀 표시 (좌표 직접 지정용) */
  crosshair?: boolean;
  caption?: string;
  className?: string;
  children?: ReactNode;
  ariaLabel?: string;
}

/**
 * 카카오 지도 SDK 구현. 화면은 `@/shared/map`의 MapView만 쓰고, 이 컴포넌트는 shared/map 안에서만 사용합니다.
 */
export function KakaoMapView({
  markers = [],
  selectedId,
  onMarkerClick,
  center: initialCenter,
  metersPerPixel: initialMpp,
  path,
  showZoom = true,
  onCenterChange,
  onBoundsChange,
  focus,
  crosshair,
  caption,
  className,
  children,
  ariaLabel = "지도",
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const overlaysRef = useRef<kakao.maps.CustomOverlay[]>([]);
  const polylineRef = useRef<kakao.maps.Polyline | null>(null);
  const focusedKeyRef = useRef<string | null>(null);
  const fittedKeyRef = useRef<string | null>(null);
  const suppressMoveRef = useRef(false);

  const onCenterChangeRef = useRef(onCenterChange);
  const onBoundsChangeRef = useRef(onBoundsChange);
  const onMarkerClickRef = useRef(onMarkerClick);
  useEffect(() => {
    onCenterChangeRef.current = onCenterChange;
    onBoundsChangeRef.current = onBoundsChange;
    onMarkerClickRef.current = onMarkerClick;
  });

  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const pins = markers.filter((m) => m.tone !== "poi");
  const pinCount = pins.length;
  const markerKey = pins.map((m) => `${m.id}:${m.position.lat},${m.position.lng}`).join("|");

  // SDK 로드 + 지도 생성
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    let cancelled = false;
    let removeIdle: (() => void) | undefined;
    let removeCenter: (() => void) | undefined;
    let removeZoom: (() => void) | undefined;
    let ro: ResizeObserver | undefined;

    loadKakaoSdk()
      .then(() => {
        if (cancelled || !containerRef.current) return;

        const center = initialCenter ?? DEFAULT_CENTER;
        const level = metersPerPixelToLevel(initialMpp ?? 4);
        const map = new kakao.maps.Map(containerRef.current, {
          center: toKakaoLatLng(center),
          level,
        });
        mapRef.current = map;
        setStatus("ready");

        const emitBounds = () => {
          if (!mapRef.current) return;
          onBoundsChangeRef.current?.(fromKakaoBounds(mapRef.current.getBounds()));
        };
        const emitCenter = () => {
          if (!mapRef.current || suppressMoveRef.current) return;
          onCenterChangeRef.current?.(fromKakaoLatLng(mapRef.current.getCenter()));
        };

        const onIdle = () => {
          emitBounds();
          suppressMoveRef.current = false;
        };
        kakao.maps.event.addListener(map, "idle", onIdle);
        kakao.maps.event.addListener(map, "center_changed", emitCenter);
        kakao.maps.event.addListener(map, "zoom_changed", emitBounds);
        removeIdle = () => kakao.maps.event.removeListener(map, "idle", onIdle);
        removeCenter = () => kakao.maps.event.removeListener(map, "center_changed", emitCenter);
        removeZoom = () => kakao.maps.event.removeListener(map, "zoom_changed", emitBounds);

        emitBounds();

        ro = new ResizeObserver(() => {
          map.relayout();
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
      removeIdle?.();
      removeCenter?.();
      removeZoom?.();
      ro?.disconnect();
      clearOverlays(overlaysRef);
      polylineRef.current?.setMap(null);
      polylineRef.current = null;
      mapRef.current = null;
    };
    // 최초 마운트 시 한 번만 생성 (initialCenter/mpp는 첫 값만 사용)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // focus 이동
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !focus || focus.key === focusedKeyRef.current) return;
    focusedKeyRef.current = focus.key;
    suppressMoveRef.current = true;
    map.panTo(toKakaoLatLng(focus.position));
    if (!focus.keepZoom) {
      const next = Math.min(map.getLevel(), metersPerPixelToLevel(2.6));
      map.setLevel(next, { animate: true });
    }
  }, [focus]);

  // 마커 구성이 바뀌면 전체가 보이도록 맞춤 (center를 밖에서 준 경우·focus 중이면 생략)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;
    if (initialCenter || focusedKeyRef.current || fittedKeyRef.current === markerKey) return;
    fittedKeyRef.current = markerKey;
    fitToMarkers(map, pins);
  }, [markerKey, status, initialCenter, pins]);

  // 마커 오버레이 동기화
  useEffect(() => {
    const map = mapRef.current;
    if (!map || status !== "ready") return;

    clearOverlays(overlaysRef);

    for (const m of markers) {
      const selected = m.id === selectedId;
      const content = createMarkerElement(m, selected, pinCount, () => onMarkerClickRef.current?.(m.id));
      const overlay = new kakao.maps.CustomOverlay({
        map,
        position: toKakaoLatLng(m.position),
        content,
        xAnchor: 0.5,
        yAnchor: m.tone === "poi" ? 0.5 : 1,
        zIndex: selected ? 3 : m.tone === "poi" ? 1 : 2,
        clickable: true,
      });
      overlaysRef.current.push(overlay);
    }
  }, [markers, selectedId, pinCount, status]);

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
      getComputedStyle(document.documentElement).getPropertyValue("--color-primary-deep").trim() || "#0f6b4c";
    const kakaoPath = path.map(toKakaoLatLng);

    if (polylineRef.current) {
      polylineRef.current.setPath(kakaoPath);
      polylineRef.current.setMap(map);
      return;
    }

    polylineRef.current = new kakao.maps.Polyline({
      map,
      path: kakaoPath,
      strokeWeight: 3,
      strokeColor,
      strokeOpacity: 0.9,
      strokeStyle: "dash",
    });
  }, [path, status]);

  const zoom = (delta: number) => {
    const map = mapRef.current;
    if (!map) return;
    map.setLevel(Math.min(14, Math.max(1, map.getLevel() + delta)), { animate: true });
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
          <button type="button" aria-label="확대" onClick={() => zoom(-1)} className="flex size-9 items-center justify-center active:bg-fill">
            <Plus size={18} />
          </button>
          <span className="h-px bg-line" />
          <button type="button" aria-label="축소" onClick={() => zoom(1)} className="flex size-9 items-center justify-center active:bg-fill">
            <Minus size={18} />
          </button>
        </div>
      )}

      {caption && <span className="pointer-events-none absolute bottom-2 left-3 z-10 text-[10px] text-ink-faint">{caption}</span>}
      {children}
    </div>
  );
}

function clearOverlays(ref: { current: kakao.maps.CustomOverlay[] }) {
  for (const overlay of ref.current) overlay.setMap(null);
  ref.current = [];
}

function createMarkerElement(
  m: MapMarker,
  selected: boolean,
  pinCount: number,
  onClick: () => void,
): HTMLElement {
  const wrap = document.createElement("button");
  wrap.type = "button";
  wrap.setAttribute("aria-label", m.title ? `${m.label ? `${m.label}번 ` : ""}${m.title}` : m.title ?? `장소 ${m.label ?? ""}`);
  if (selected) wrap.setAttribute("aria-pressed", "true");
  wrap.style.cssText = "display:flex;flex-direction:column;align-items:center;border:0;background:transparent;padding:0;cursor:pointer;";
  wrap.addEventListener("click", (e) => {
    e.stopPropagation();
    onClick();
  });

  const primary = cssVar("--color-primary", "#22c55e");
  const primaryDeep = cssVar("--color-primary-deep", "#0f6b4c");
  const onPrimary = cssVar("--color-on-primary", "#ffffff");
  const ink = cssVar("--color-ink", "#171717");
  const inkFaint = cssVar("--color-ink-faint", "#a3a3a3");

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
    `display:flex;align-items:center;justify-content:center;`,
    `width:${size}px;height:${size}px;border-radius:9999px;`,
    `border:2px solid #fff;background:${bg};color:${color};`,
    `font-size:12px;font-weight:700;box-shadow:0 1px 4px rgba(0,0,0,0.25);`,
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

function fitToMarkers(map: kakao.maps.Map, markers: MapMarker[]) {
  if (markers.length === 0) {
    map.setCenter(toKakaoLatLng(DEFAULT_CENTER));
    map.setLevel(metersPerPixelToLevel(4));
    return;
  }

  if (markers.length === 1) {
    map.setCenter(toKakaoLatLng(markers[0].position));
    map.setLevel(metersPerPixelToLevel(2.6));
    return;
  }

  const bounds = new kakao.maps.LatLngBounds();
  for (const m of markers) bounds.extend(toKakaoLatLng(m.position));
  map.setBounds(bounds, FIT_PADDING, FIT_PADDING, FIT_PADDING, FIT_PADDING);

  if (map.getLevel() > FIT_MAX_LEVEL) {
    const anchor = markers[0].position;
    const near = markers.filter((m) => {
      const dLat = (m.position.lat - anchor.lat) * 111_320;
      const dLng =
        (m.position.lng - anchor.lng) * 111_320 * Math.cos((anchor.lat * Math.PI) / 180);
      return Math.hypot(dLat, dLng) < 2500;
    });
    const nearBounds = new kakao.maps.LatLngBounds();
    for (const m of near) nearBounds.extend(toKakaoLatLng(m.position));
    map.setBounds(nearBounds, FIT_PADDING, FIT_PADDING, FIT_PADDING, FIT_PADDING);
    if (map.getLevel() > FIT_MAX_LEVEL) map.setLevel(FIT_MAX_LEVEL);
  }
}
