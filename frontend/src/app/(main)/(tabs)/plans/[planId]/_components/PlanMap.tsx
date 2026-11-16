"use client";

import { Clock3 } from "lucide-react";
import type { PlanItem } from "@/entities/plan";
import { MapView } from "@/shared/map";

/** 일정 지도: 번호 핀이 목록 순서와 같고, 고정 시간이 있는 곳은 진한 핀으로 표시합니다. */
export function PlanMap({ items }: { items: PlanItem[] }) {
  return (
    <div>
      <MapView
        className="-mx-5 h-[46dvh] min-h-72"
        markers={items.map((it, i) => ({
          id: it.id,
          position: it.place.location,
          label: String(i + 1),
          title: it.place.name,
          tone: it.fixedTime ? ("fixed" as const) : ("primary" as const),
        }))}
        path={items.map((i) => i.place.location)}
        caption="예시 지도 · 경로는 순서를 이은 선이에요"
      />
      <ol className="mt-3 divide-y divide-line">
        {items.map((it, i) => (
          <li key={it.id} className="flex items-center gap-3 py-2.5">
            <span className="flex size-6 items-center justify-center rounded-full bg-selected text-[11px] font-bold text-primary-deep">{i + 1}</span>
            <span className="min-w-0 flex-1 truncate text-[14px]">{it.place.name}</span>
            {it.fixedTime && (
              <span className="inline-flex items-center gap-1 text-[12px] font-bold text-primary-deep tabular-nums">
                <Clock3 size={12} /> {it.fixedTime}
              </span>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
