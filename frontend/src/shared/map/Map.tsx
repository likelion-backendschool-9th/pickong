"use client";

import { Lock, Minus, Plus } from "lucide-react";
import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import type { LatLng, LatLngBounds, MapMarker } from "./types";

/**
 * 지도 컴포넌트 (현재: 개념 지도 일러스트).
 *
 * 화면은 이 컴포넌트의 props만 사용합니다. 카카오 지도 SDK를 연결할 때는
 * shared/map 안에서 같은 props를 받는 구현으로 바꾸면 되고, 화면 코드는 바뀌지 않습니다.
 * 도로·공원 배경은 예시 그림이며 실제 지도 데이터가 아닙니다.
 */

const M_PER_DEG_LAT = 111_320;
const ORIGIN: LatLng = { lat: 37.5446, lng: 127.048 };
const TILE = 280; // 배경 타일 크기(px) @ 기준 축척
const REF_MPP = 3.2; // 기준 축척 (m/px)
const MIN_MPP = 0.8;
const MAX_MPP = 400;

const AREA_LABELS: { text: string; position: LatLng }[] = [
  { text: "성수동", position: { lat: 37.5458, lng: 127.0548 } },
  { text: "서울숲", position: { lat: 37.5452, lng: 127.0392 } },
  { text: "뚝섬", position: { lat: 37.5305, lng: 127.0665 } },
  { text: "망원동", position: { lat: 37.5565, lng: 126.9045 } },
  { text: "종로", position: { lat: 37.5815, lng: 126.984 } },
  { text: "상암동", position: { lat: 37.5688, lng: 126.8795 } },
];

interface MapProps {
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
  /** 중앙 고정 핀 표시 (좌표 직접 지정용) */
  crosshair?: boolean;
  caption?: string;
  className?: string;
  children?: ReactNode;
  ariaLabel?: string;
}

export function MapView({
  markers = [],
  selectedId,
  onMarkerClick,
  center: initialCenter,
  metersPerPixel: initialMpp,
  path,
  showZoom = true,
  onCenterChange,
  onBoundsChange,
  crosshair,
  caption = "예시 지도 · 위치는 확인이 필요해요",
  className,
  children,
  ariaLabel = "지도",
}: MapProps) {
  const patternId = useId().replace(/:/g, "");
  const ref = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [view, setView] = useState<{ center: LatLng; mpp: number } | null>(
    initialCenter ? { center: initialCenter, mpp: initialMpp ?? 3 } : null,
  );
  const drag = useRef<{ x: number; y: number; center: LatLng; moved: boolean } | null>(null);

  const markerKey = markers.map((m) => `${m.id}:${m.position.lat},${m.position.lng}`).join("|");

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => {
      setSize({ w: entry.contentRect.width, h: entry.contentRect.height });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // 마커 구성이 바뀌면 전체가 보이도록 다시 맞춥니다.
  const fitted = useRef<string | null>(null);
  useEffect(() => {
    if (initialCenter || !size.w || fitted.current === markerKey) return;
    fitted.current = markerKey;
    setView(fitView(markers, size));
  }, [markerKey, size, initialCenter, markers]);

  const current = view ?? fitView(markers, size);
  const cosLat = Math.cos((current.center.lat * Math.PI) / 180);

  const project = useCallback(
    (p: LatLng) => ({
      x: size.w / 2 + ((p.lng - current.center.lng) * M_PER_DEG_LAT * cosLat) / current.mpp,
      y: size.h / 2 - ((p.lat - current.center.lat) * M_PER_DEG_LAT) / current.mpp,
    }),
    [size, current.center.lat, current.center.lng, current.mpp, cosLat],
  );

  // 보이는 범위 알림: 중심 · 축척 · 크기가 바뀔 때마다
  const onBoundsChangeRef = useRef(onBoundsChange);
  useEffect(() => {
    onBoundsChangeRef.current = onBoundsChange;
  });
  const { lat: cLat, lng: cLng } = current.center;
  useEffect(() => {
    if (!size.w || !onBoundsChangeRef.current) return;
    const halfLat = ((size.h / 2) * current.mpp) / M_PER_DEG_LAT;
    const halfLng = ((size.w / 2) * current.mpp) / (M_PER_DEG_LAT * cosLat);
    onBoundsChangeRef.current({ north: cLat + halfLat, south: cLat - halfLat, east: cLng + halfLng, west: cLng - halfLng });
  }, [cLat, cLng, current.mpp, size.w, size.h, cosLat]);

  const updateView = (next: { center: LatLng; mpp: number }) => {
    setView(next);
    onCenterChange?.(next.center);
  };

  const zoom = (factor: number) => {
    const mpp = Math.min(MAX_MPP, Math.max(MIN_MPP, current.mpp * factor));
    updateView({ center: current.center, mpp });
  };

  const onPointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest("button")) return;
    drag.current = { x: e.clientX, y: e.clientY, center: current.center, moved: false };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    const d = drag.current;
    if (!d) return;
    const dx = e.clientX - d.x;
    const dy = e.clientY - d.y;
    if (Math.abs(dx) + Math.abs(dy) > 3) d.moved = true;
    updateView({
      mpp: current.mpp,
      center: {
        lat: d.center.lat + (dy * current.mpp) / M_PER_DEG_LAT,
        lng: d.center.lng - (dx * current.mpp) / (M_PER_DEG_LAT * cosLat),
      },
    });
  };
  const onPointerUp = () => {
    drag.current = null;
  };

  // 배경 타일 위치: 고정 기준점(ORIGIN)을 화면 좌표로 옮겨서 지도와 함께 움직이게 합니다.
  const origin = project(ORIGIN);
  // 축소할수록 배경 타일이 점처럼 작아지지 않도록 그림 축척은 범위를 제한합니다 (배경은 예시 그림).
  const scale = Math.min(2.5, Math.max(0.45, REF_MPP / current.mpp));
  const tile = TILE * scale;

  const pathD = useMemo(() => {
    if (!path || path.length < 2 || !size.w) return null;
    return path.map((p, i) => {
      const { x, y } = project(p);
      return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(" ");
  }, [path, project, size.w]);

  return (
    <div
      ref={ref}
      role="application"
      aria-label={ariaLabel}
      className={cn("relative touch-none overflow-hidden bg-map-land select-none", className)}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      <svg className="absolute inset-0 size-full" aria-hidden>
        <defs>
          <pattern
            id={patternId}
            width={TILE}
            height={TILE}
            patternUnits="userSpaceOnUse"
            patternTransform={`translate(${origin.x % tile} ${origin.y % tile}) scale(${scale})`}
          >
            <rect width={TILE} height={TILE} fill="var(--color-map-land)" />
            <rect x={150} y={22} width={108} height={88} rx={14} fill="var(--color-map-park)" />
            <rect x={18} y={176} width={70} height={74} rx={10} fill="var(--color-map-park)" opacity={0.7} />
            <g stroke="#e3e6de" strokeWidth={16} strokeLinecap="round">
              <path d="M0 136 H280" />
              <path d="M118 0 V280" />
            </g>
            <g stroke="var(--color-map-road)" strokeWidth={12} strokeLinecap="round">
              <path d="M0 136 H280" />
              <path d="M118 0 V280" />
            </g>
            <g stroke="var(--color-map-road)" strokeWidth={6} strokeLinecap="round">
              <path d="M0 58 H110" />
              <path d="M126 212 H280" />
              <path d="M232 136 V280" />
              <path d="M48 0 V130" />
              <path d="M126 150 L270 10" opacity={0.9} />
            </g>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill={`url(#${patternId})`} />
        {pathD && (
          <path
            d={pathD}
            fill="none"
            stroke="var(--color-primary-deep)"
            strokeWidth={3}
            strokeDasharray="6 6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
      </svg>

      {size.w > 0 &&
        AREA_LABELS.map((label) => {
          const { x, y } = project(label.position);
          if (x < -40 || y < -20 || x > size.w + 40 || y > size.h + 20) return null;
          return (
            <span
              key={label.text}
              className="pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 text-[11px] font-medium text-ink-faint"
              style={{ left: x, top: y }}
            >
              {label.text}
            </span>
          );
        })}

      {size.w > 0 &&
        markers.map((m) => {
          const { x, y } = project(m.position);
          const selected = m.id === selectedId;
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => onMarkerClick?.(m.id)}
              aria-label={m.title ? `${m.label ? `${m.label}번 ` : ""}${m.title}` : `장소 ${m.label ?? ""}`}
              aria-pressed={onMarkerClick ? selected : undefined}
              className="absolute flex -translate-x-1/2 -translate-y-1/2 flex-col items-center"
              style={{ left: x, top: y, zIndex: selected ? 3 : 2 }}
            >
              <span
                className={cn(
                  "flex items-center justify-center rounded-full border-2 border-white text-[12px] font-bold shadow-[0_1px_4px_rgba(0,0,0,0.25)] transition-transform",
                  m.tone === "muted" && "bg-white text-ink",
                  m.tone === "fixed" && "bg-primary-deep text-white",
                  (!m.tone || m.tone === "primary") && "bg-primary text-on-primary",
                  selected ? "size-9 scale-110 ring-[3px] ring-primary-deep/60" : "size-7",
                )}
              >
                {m.tone === "fixed" && !m.label ? <Lock size={12} /> : m.label}
              </span>
              {m.title && (selected || markers.length <= 6) && (
                <span className="mt-0.5 max-w-24 truncate rounded bg-white/90 px-1 text-[10px] font-bold text-ink">
                  {m.title}
                </span>
              )}
            </button>
          );
        })}

      {crosshair && (
        <div className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-full" aria-hidden>
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

      {showZoom && (
        <div className="absolute right-3 bottom-8 flex flex-col overflow-hidden rounded-lg border border-line bg-white shadow-sm">
          <button type="button" aria-label="확대" onClick={() => zoom(1 / 1.6)} className="flex size-9 items-center justify-center active:bg-fill">
            <Plus size={18} />
          </button>
          <span className="h-px bg-line" />
          <button type="button" aria-label="축소" onClick={() => zoom(1.6)} className="flex size-9 items-center justify-center active:bg-fill">
            <Minus size={18} />
          </button>
        </div>
      )}

      {caption && (
        <span className="pointer-events-none absolute bottom-2 left-3 text-[10px] text-ink-faint">{caption}</span>
      )}
      {children}
    </div>
  );
}

const FIT_MAX_MPP = 9;

function boundsView(markers: MapMarker[], size: { w: number; h: number }) {
  const lats = markers.map((m) => m.position.lat);
  const lngs = markers.map((m) => m.position.lng);
  const center = {
    lat: (Math.min(...lats) + Math.max(...lats)) / 2,
    lng: (Math.min(...lngs) + Math.max(...lngs)) / 2,
  };
  if (markers.length === 1 || !size.w) return { center, mpp: 2.6 };
  const cosLat = Math.cos((center.lat * Math.PI) / 180);
  const spanX = (Math.max(...lngs) - Math.min(...lngs)) * M_PER_DEG_LAT * cosLat;
  const spanY = (Math.max(...lats) - Math.min(...lats)) * M_PER_DEG_LAT;
  const pad = 56;
  const mpp = Math.max(spanX / Math.max(40, size.w - pad * 2), spanY / Math.max(40, size.h - pad * 2), 1.5);
  return { center, mpp: Math.min(MAX_MPP, mpp) };
}

/**
 * 처음 보여줄 범위. 전체 마커를 맞추되, 너무 넓게 퍼져 있으면(다른 동네 장소 포함)
 * 첫 번째 마커 주변 장소들만 맞춥니다. 나머지는 지도를 움직이거나 축소해서 볼 수 있습니다.
 */
function fitView(markers: MapMarker[], size: { w: number; h: number }) {
  if (markers.length === 0) return { center: { lat: 37.5446, lng: 127.0525 }, mpp: 4 };
  const all = boundsView(markers, size);
  if (all.mpp <= FIT_MAX_MPP) return all;
  const anchor = markers[0].position;
  const near = markers.filter((m) => {
    const dLat = (m.position.lat - anchor.lat) * M_PER_DEG_LAT;
    const dLng = (m.position.lng - anchor.lng) * M_PER_DEG_LAT * Math.cos((anchor.lat * Math.PI) / 180);
    return Math.hypot(dLat, dLng) < 2500;
  });
  const view = boundsView(near, size);
  return { center: view.center, mpp: Math.min(view.mpp, FIT_MAX_MPP) };
}
