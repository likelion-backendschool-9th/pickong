"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

interface CarouselProps {
  count: number;
  index: number;
  onIndexChange: (index: number) => void;
  renderSlide: (index: number) => ReactNode;
  className?: string;
  label?: string;
}

/**
 * 가로 스와이프 사진 캐러셀. 현재 번호(1 / 5)를 우측 상단에 표시합니다.
 * index는 부모가 가지고 있어서, 시트를 열고 닫아도 보고 있던 사진이 유지됩니다.
 */
export function Carousel({ count, index, onIndexChange, renderSlide, className, label = "사진" }: CarouselProps) {
  const ref = useRef<HTMLDivElement>(null);
  const settling = useRef(false);

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
        tabIndex={0}
        className="scrollbar-none flex size-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain"
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
