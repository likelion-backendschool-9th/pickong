"use client";

import { ArrowDown } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import { Spinner } from "./display";

/** 이만큼(px) 당겼다 놓으면 새로고침 */
const THRESHOLD = 64;
/** 당긴 거리 대비 실제 이동 비율 (손가락보다 덜 움직여 묵직하게) */
const RESISTANCE = 0.5;
const MAX_PULL = 110;
/** 새로고침 표시를 최소 이 시간(ms) 보여 줘서 깜빡임을 막습니다. */
const MIN_SPIN = 500;

/**
 * 당겨서 새로고침 (터치).
 * 페이지가 맨 위일 때 아래로 당겼다 놓으면 onRefresh를 실행합니다.
 * 가로로 움직이기 시작한 손가락(사진 넘기기)은 무시합니다.
 * refreshSignal 값이 바뀌면 당겼다 놓은 것과 같은 모습으로 새로고침합니다 (홈 버튼 다시 누르기 등).
 */
export function PullToRefresh({
  onRefresh,
  children,
  disabled,
  refreshSignal = 0,
}: {
  onRefresh: () => Promise<unknown>;
  children: ReactNode;
  disabled?: boolean;
  refreshSignal?: number;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [pull, setPull] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const pullRef = useRef(0);
  const onRefreshRef = useRef(onRefresh);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  });

  // 외부 신호로 새로고침: 당긴 위치에 표시를 띄우고 새로고침이 끝나면 접습니다.
  useEffect(() => {
    if (!refreshSignal) return;
    let cancelled = false;
    const run = async () => {
      pullRef.current = THRESHOLD * 0.75;
      setPull(THRESHOLD * 0.75);
      setRefreshing(true);
      try {
        await Promise.all([onRefreshRef.current(), new Promise((r) => setTimeout(r, MIN_SPIN))]);
      } finally {
        if (!cancelled) {
          setRefreshing(false);
          pullRef.current = 0;
          setPull(0);
        }
      }
    };
    run();
    return () => {
      cancelled = true;
    };
  }, [refreshSignal]);

  useEffect(() => {
    const el = rootRef.current;
    if (!el || disabled || refreshing) return;

    let start: { x: number; y: number } | null = null;
    let pulling = false;

    const setDistance = (v: number) => {
      pullRef.current = v;
      setPull(v);
    };

    const onStart = (e: TouchEvent) => {
      start = window.scrollY <= 0 && e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
      pulling = false;
    };

    const onMove = (e: TouchEvent) => {
      if (!start) return;
      const dx = e.touches[0].clientX - start.x;
      const dy = e.touches[0].clientY - start.y;
      if (!pulling) {
        // 방향이 정해지기 전: 가로 이동이나 위로 미는 동작이면 이번 터치는 포기
        if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) return void (start = null);
        if (dy < -8) return void (start = null);
        if (dy < 8 || window.scrollY > 0) return;
        pulling = true;
        setDragging(true);
      }
      // 브라우저 기본 동작(바운스 · 페이지 새로고침) 대신 직접 당김 처리
      if (e.cancelable) e.preventDefault();
      setDistance(Math.min(MAX_PULL, Math.max(0, dy * RESISTANCE)));
    };

    const onEnd = async () => {
      if (!pulling) {
        start = null;
        return;
      }
      start = null;
      pulling = false;
      setDragging(false);
      if (pullRef.current < THRESHOLD) return setDistance(0);

      setRefreshing(true);
      setDistance(THRESHOLD * 0.75);
      const minSpin = new Promise((r) => setTimeout(r, MIN_SPIN));
      try {
        await Promise.all([onRefreshRef.current(), minSpin]);
      } finally {
        setRefreshing(false);
        setDistance(0);
      }
    };

    el.addEventListener("touchstart", onStart, { passive: true });
    el.addEventListener("touchmove", onMove, { passive: false });
    el.addEventListener("touchend", onEnd);
    el.addEventListener("touchcancel", onEnd);
    return () => {
      el.removeEventListener("touchstart", onStart);
      el.removeEventListener("touchmove", onMove);
      el.removeEventListener("touchend", onEnd);
      el.removeEventListener("touchcancel", onEnd);
    };
  }, [disabled, refreshing]);

  const ready = pull >= THRESHOLD;

  return (
    <div ref={rootRef} className="relative">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 flex justify-center overflow-hidden"
        style={{ height: pull }}
        aria-hidden={!refreshing}
      >
        <div className="flex items-center gap-1.5 self-end pb-3 text-[12px] font-medium text-ink-sub">
          {refreshing ? (
            <Spinner className="size-5" label="새로고침 중" />
          ) : (
            <>
              <ArrowDown size={16} className={cn("transition-transform duration-200", ready && "rotate-180")} />
              {ready ? "놓으면 새로고침" : "당겨서 새로고침"}
            </>
          )}
        </div>
      </div>
      <div
        className={cn(!dragging && "transition-transform duration-300 ease-out motion-reduce:transition-none")}
        style={{ transform: pull ? `translateY(${pull}px)` : undefined }}
      >
        {children}
      </div>
    </div>
  );
}
