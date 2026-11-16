"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

interface CarouselProps {
  count: number;
  index: number;
  onIndexChange: (index: number) => void;
  renderSlide: (index: number) => ReactNode;
  className?: string;
  label?: string;
}

/** 드래그로 넘길 때 기준: 폭의 20% 이상 끌거나, 빠르게 튕기면 다음 사진 */
const SWIPE_RATIO = 0.2;
const FLICK_VELOCITY = 0.4; // px/ms

/**
 * 가로 스와이프 사진 캐러셀. 현재 번호(1 / 5)를 우측 상단에 표시합니다.
 * index는 부모가 가지고 있어서, 시트를 열고 닫아도 보고 있던 사진이 유지됩니다.
 * 터치는 브라우저 기본 스크롤 스냅으로 넘기고, 마우스는 끌어서(드래그) 넘길 수 있습니다.
 */
export function Carousel({ count, index, onIndexChange, renderSlide, className, label = "사진" }: CarouselProps) {
  const ref = useRef<HTMLDivElement>(null);
  const settling = useRef(false);
  const drag = useRef<{ x: number; left: number; time: number; from: number; moved: boolean } | null>(null);

  // 외부에서 index가 바뀌면 해당 위치로 이동
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const target = index * el.clientWidth;
    if (Math.abs(el.scrollLeft - target) > 2) {
      settling.current = true;
      el.scrollTo({ left: target, behavior: "smooth" });
      const t = setTimeout(() => (settling.current = false), 400);
      return () => clearTimeout(t);
    }
  }, [index]);

  const onScroll = () => {
    const el = ref.current;
    if (!el || settling.current) return;
    const next = Math.round(el.scrollLeft / el.clientWidth);
    if (next !== index && next >= 0 && next < count) onIndexChange(next);
  };

  const go = (next: number) => onIndexChange(Math.max(0, Math.min(count - 1, next)));

  // 마우스 드래그: 끄는 동안은 스냅을 끄고 직접 따라 움직인 뒤, 놓으면 가까운 사진으로 맞춥니다.
  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    const el = ref.current;
    if (!el || e.pointerType !== "mouse" || e.button !== 0 || count < 2) return;
    drag.current = { x: e.clientX, left: el.scrollLeft, time: performance.now(), from: index, moved: false };
    el.setPointerCapture(e.pointerId);
    el.style.scrollSnapType = "none";
    settling.current = true;
  };

  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    const el = ref.current;
    if (!d || !el) return;
    const dx = e.clientX - d.x;
    if (Math.abs(dx) > 4) d.moved = true;
    el.scrollLeft = d.left - dx;
  };

  const endDrag = (e: ReactPointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    const el = ref.current;
    if (!d || !el) return;
    drag.current = null;
    const dx = e.clientX - d.x;
    const velocity = dx / Math.max(1, performance.now() - d.time);
    const width = el.clientWidth;
    let next = d.from;
    if (dx < -width * SWIPE_RATIO || velocity < -FLICK_VELOCITY) next = d.from + 1;
    else if (dx > width * SWIPE_RATIO || velocity > FLICK_VELOCITY) next = d.from - 1;
    next = Math.max(0, Math.min(count - 1, next));
    el.scrollTo({ left: next * width, behavior: "smooth" });
    setTimeout(() => {
      el.style.scrollSnapType = "";
      settling.current = false;
    }, 350);
    if (next !== index) onIndexChange(next);
    // 끌다가 놓은 직후의 클릭은 무시 (사진 링크 등이 열리지 않게)
    if (d.moved) {
      const swallow = (ev: MouseEvent) => {
        ev.preventDefault();
        ev.stopPropagation();
      };
      el.addEventListener("click", swallow, { capture: true, once: true });
      setTimeout(() => el.removeEventListener("click", swallow, { capture: true }), 0);
    }
  };

  return (
    <div
      className={cn("group relative", className)}
      role="region"
      aria-roledescription="carousel"
      aria-label={label}
      onKeyDown={(e) => {
        if (e.key === "ArrowRight") go(index + 1);
        if (e.key === "ArrowLeft") go(index - 1);
      }}
    >
      <div
        ref={ref}
        onScroll={onScroll}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        tabIndex={0}
        className={cn(
          "scrollbar-none flex size-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain select-none",
          count > 1 && "cursor-grab active:cursor-grabbing",
        )}
      >
        {Array.from({ length: count }, (_, i) => (
          <div
            key={i}
            className="relative size-full shrink-0 snap-start snap-always"
            aria-roledescription="slide"
            aria-label={`${i + 1} / ${count}`}
            aria-hidden={i !== index}
          >
            {renderSlide(i)}
          </div>
        ))}
      </div>
      {count > 1 && (
        <>
          <span className="pointer-events-none absolute top-3 right-3 rounded-full bg-black/45 px-2.5 py-1 text-[12px] font-medium text-white tabular-nums">
            {index + 1} / {count}
          </span>
          {index > 0 && (
            <button
              type="button"
              aria-label="이전 사진"
              onClick={() => go(index - 1)}
              className="absolute top-1/2 left-2 hidden size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-ink group-hover:flex"
            >
              <ChevronLeft size={18} />
            </button>
          )}
          {index < count - 1 && (
            <button
              type="button"
              aria-label="다음 사진"
              onClick={() => go(index + 1)}
              className="absolute top-1/2 right-2 hidden size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-ink group-hover:flex"
            >
              <ChevronRight size={18} />
            </button>
          )}
        </>
      )}
    </div>
  );
}

export function Dots({ count, index }: { count: number; index: number }) {
  if (count <= 1) return null;
  return (
    <div className="flex justify-center gap-1" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className={cn("size-1.5 rounded-full transition-colors", i === index ? "bg-primary-deep" : "bg-line")} />
      ))}
    </div>
  );
}
