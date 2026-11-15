"use client";

import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { AlertTriangle, Check, Footprints, GripVertical, Plus, TrainFront } from "lucide-react";
import { categoryLabel } from "@/entities/place";
import { canDrag, ReservationCard, type Plan, type PlanItem, type Timeline, type TimelineEntry } from "@/entities/plan";
import { cn } from "@/shared/lib/cn";
import { formatDuration, toHHmm } from "@/shared/lib/time";
import { describeWarning } from "../_lib/warningText";

const GAP_MIN = 30;

interface PlanTimelineProps {
  plan: Plan;
  items: PlanItem[];
  timeline: Timeline;
  readOnly: boolean;
  onMove: (from: number, to: number) => void;
  onWarning: (itemId: string) => void;
  onStay: (itemId: string) => void;
  onGap: (index: number) => void;
}

/** 하루 타임라인: 확정 예약은 고정, 자유 일정은 드래그 핸들로 순서 변경 */
export function PlanTimeline({ plan, items, timeline, readOnly, onMove, onWarning, onStay, onGap }: PlanTimelineProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    onMove(
      items.findIndex((i) => i.id === active.id),
      items.findIndex((i) => i.id === over.id),
    );
  };

  return (
    <div>
      <div className="flex items-center gap-3 pb-2">
        <span className="flex size-7 items-center justify-center rounded-full bg-ink text-[11px] font-bold text-white">출발</span>
        <span className="text-[14px] font-bold tabular-nums">{plan.startTime}</span>
        <span className="text-[14px]">{plan.startPlace.name}</span>
      </div>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={onDragEnd}
        accessibility={{
          screenReaderInstructions: { draggable: "스페이스로 들어 올리고 화살표로 옮긴 뒤 스페이스로 놓아요." },
        }}
      >
        <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
          <ol>
            {items.map((item, index) => {
              const entry = timeline.entries[index];
              return (
                <li key={item.id}>
                  <Connector entry={entry} />
                  {entry.waitMinutes >= GAP_MIN && item.kind === "RESERVATION" && !readOnly && (
                    <button
                      type="button"
                      onClick={() => onGap(index)}
                      className="mb-2 ml-10 flex w-[calc(100%-40px)] items-center justify-between rounded-card border border-dashed border-primary-deep/30 px-4 py-2.5 text-left"
                    >
                      <span className="text-[12px] text-ink-sub">
                        빈 시간 {formatDuration(entry.waitMinutes)} · {toHHmm(entry.arriveAt)}–{toHHmm(entry.startAt)}
                      </span>
                      <span className="flex items-center gap-1 text-[12px] font-bold text-primary-deep">
                        <Plus size={14} /> 장소 추가
                      </span>
                    </button>
                  )}
                  <StopRow
                    item={item}
                    order={index + 1}
                    entry={entry}
                    items={items}
                    readOnly={readOnly}
                    onWarning={() => onWarning(item.id)}
                    onStay={() => onStay(item.id)}
                  />
                </li>
              );
            })}
          </ol>
        </SortableContext>
      </DndContext>
      <div className="mt-3 flex items-center gap-3">
        <span className="flex size-7 items-center justify-center rounded-full bg-fill text-[11px] font-bold text-ink-sub">끝</span>
        <span className="text-[13px] text-ink-sub tabular-nums">
          {toHHmm(timeline.endAt)} 일정 종료 · 희망 {plan.endTime}
          {timeline.overEndMinutes > 0 && <span className="text-warning"> · {formatDuration(timeline.overEndMinutes)} 초과</span>}
        </span>
      </div>
    </div>
  );
}

function Connector({ entry }: { entry: TimelineEntry }) {
  const Icon = entry.leg.mode === "TRANSIT" ? TrainFront : Footprints;
  return (
    <div className="flex items-center gap-3 py-1.5 pl-3">
      <span className="h-6 w-px bg-line" aria-hidden />
      <span className={cn("flex items-center gap-1 pl-4 text-[11px]", entry.leg.isEstimate ? "text-ink-faint italic" : "text-ink-sub")}>
        <Icon size={12} />
        {entry.leg.isEstimate ? `이동 시간 계산 중 · 약 ${entry.leg.minutes}분` : `${entry.leg.mode === "TRANSIT" ? "대중교통" : "도보"} ${entry.leg.minutes}분`}
      </span>
    </div>
  );
}

function StopRow({
  item,
  order,
  entry,
  items,
  readOnly,
  onWarning,
  onStay,
}: {
  item: PlanItem;
  order: number;
  entry: TimelineEntry;
  items: PlanItem[];
  readOnly: boolean;
  onWarning: () => void;
  onStay: () => void;
}) {
  const draggable = canDrag(item) && !readOnly;
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !draggable,
  });
  const warning = entry.warnings[0];
  const w = warning ? describeWarning(warning, item, items) : null;
  const estimate = entry.leg.isEstimate;

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn("relative flex gap-3", isDragging && "z-10 opacity-90")}
    >
      <span
        className={cn(
          "mt-3 flex size-7 shrink-0 items-center justify-center rounded-full text-[12px] font-bold",
          item.visited ? "bg-selected text-primary-deep" : item.kind === "RESERVATION" ? "bg-primary-deep text-white" : "bg-primary text-on-primary",
        )}
        aria-label={`${order}번째`}
      >
        {item.visited ? <Check size={14} strokeWidth={3} /> : order}
      </span>

      {item.kind === "RESERVATION" ? (
        <ReservationCard item={item} className="min-w-0 flex-1">
          {w && <WarningChip text={w.short} onClick={onWarning} />}
        </ReservationCard>
      ) : (
        <div
          className={cn(
            "flex min-w-0 flex-1 items-start gap-2 rounded-card border bg-bg px-4 py-3",
            isDragging ? "border-primary shadow-lg" : "border-line",
          )}
        >
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-1.5">
              <span className={cn("text-[15px] font-bold tabular-nums", estimate && "text-ink-sub")}>{toHHmm(entry.startAt)}</span>
              <span className="truncate text-[15px] font-bold">{item.place.name}</span>
            </div>
            <p className="mt-0.5 text-[12px] text-ink-sub">
              {categoryLabel(item.place.category)} ·{" "}
              {readOnly ? (
                `${item.stayMinutes}분 체류`
              ) : (
                <button type="button" onClick={onStay} className="font-bold text-primary-deep underline-offset-2 hover:underline">
                  {item.stayMinutes}분 체류
                </button>
              )}{" "}
              · <span className="tabular-nums">{toHHmm(entry.departAt)}</span> 출발
              {estimate && <span className="text-ink-faint"> (임시)</span>}
            </p>
            {entry.waitMinutes > 0 && <p className="mt-0.5 text-[11px] text-ink-faint">오픈 대기 {entry.waitMinutes}분</p>}
            {entry.hoursUnknown && <p className="mt-0.5 text-[11px] text-ink-faint">영업시간 확인 필요</p>}
            {w && <WarningChip text={w.short} onClick={onWarning} />}
          </div>
          {draggable && (
            <button
              type="button"
              ref={setActivatorNodeRef}
              {...attributes}
              {...listeners}
              aria-label={`${item.place.name} 순서 옮기기`}
              className="-mr-2 flex size-10 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-ink-faint active:cursor-grabbing active:bg-fill"
            >
              <GripVertical size={20} />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function WarningChip({ text, onClick }: { text: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-2 flex items-center gap-1 rounded-md bg-warning-soft px-2 py-1 text-[12px] font-bold text-warning"
    >
      <AlertTriangle size={13} /> {text} · 해결하기
    </button>
  );
}
