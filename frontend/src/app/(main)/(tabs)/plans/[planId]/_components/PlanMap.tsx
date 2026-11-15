"use client";

import { X } from "lucide-react";
import type { Plan, PlanItem, Timeline } from "@/entities/plan";
import { MapView } from "@/shared/map";
import { toHHmm } from "@/shared/lib/time";

/** 일정 지도: 번호 핀이 타임라인 순서와 같고, 장소를 제외하면 핀과 경로가 함께 바뀝니다. */
export function PlanMap({
  plan,
  items,
  timeline,
  readOnly,
  onRemove,
}: {
  plan: Plan;
  items: PlanItem[];
  timeline: Timeline;
  readOnly: boolean;
  onRemove: (id: string) => void;
}) {
  return (
    <div>
      <MapView
        className="-mx-5 h-[46dvh] min-h-72"
        markers={[
          { id: "start", position: plan.startPlace.location, label: "S", title: plan.startPlace.name, tone: "muted" as const },
          ...items.map((it, i) => ({
            id: it.id,
            position: it.place.location,
            label: String(i + 1),
            title: it.place.name,
            tone: it.kind === "RESERVATION" ? ("fixed" as const) : ("primary" as const),
          })),
        ]}
        path={[plan.startPlace.location, ...items.map((i) => i.place.location)]}
        caption="예시 지도 · 경로는 순서를 이은 선이에요"
      />
      <ol className="mt-3 divide-y divide-line">
        {items.map((it, i) => (
          <li key={it.id} className="flex items-center gap-3 py-2.5">
            <span className="flex size-6 items-center justify-center rounded-full bg-selected text-[11px] font-bold text-primary-deep">{i + 1}</span>
            <span className="w-11 text-[13px] font-bold tabular-nums">{toHHmm(timeline.entries[i].startAt)}</span>
            <span className="min-w-0 flex-1 truncate text-[14px]">{it.place.name}</span>
            {it.kind === "RESERVATION" ? (
              <span className="text-[11px] text-primary-deep">확정 예약</span>
            ) : (
              !readOnly && (
                <button type="button" aria-label={`${it.place.name} 제외`} onClick={() => onRemove(it.id)} className="flex size-9 items-center justify-center text-ink-faint">
                  <X size={16} />
                </button>
              )
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
