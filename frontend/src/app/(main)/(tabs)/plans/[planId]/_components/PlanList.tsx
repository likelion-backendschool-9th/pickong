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
import { Check, ChevronRight, Clock3, GripVertical } from "lucide-react";
import { categoryLabel } from "@/entities/place";
import { travelEstimate, type PlanItem } from "@/entities/plan";
import { cn } from "@/shared/lib/cn";

interface PlanListProps {
  items: PlanItem[];
  /** 계획 중일 때만 순서 변경 · 항목 눌러 고정 시간 설정 */
  editable: boolean;
  onMove: (from: number, to: number) => void;
  onSelect: (itemId: string) => void;
}

/**
 * 일정 장소 목록: 순서만 정하고, 시간이 정해진 곳(영화 · 식당 예약 등)에만 고정 시간을 표시합니다.
 * 계획 중에는 핸들을 끌어 순서를 바꾸고, 장소를 누르면 고정 시간을 설정합니다.
 */
export function PlanList({ items, editable, onMove, onSelect }: PlanListProps) {
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
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragEnd={onDragEnd}
      accessibility={{ screenReaderInstructions: { draggable: "스페이스로 들어 올리고 화살표로 옮긴 뒤 스페이스로 놓아요." } }}
    >
      <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
        <ol>
          {items.map((item, index) => (
            <li key={item.id}>
              {index > 0 && (
                <p className="py-1 pl-[46px] text-[11px] text-ink-faint" aria-label={`이동 약 ${travelEstimate(items[index - 1], item)}분`}>
                  이동 약 {travelEstimate(items[index - 1], item)}분
                </p>
              )}
              <Stop item={item} order={index + 1} editable={editable} onSelect={() => onSelect(item.id)} />
            </li>
          ))}
        </ol>
      </SortableContext>
    </DndContext>
  );
}

function Stop({ item, order, editable, onSelect }: { item: PlanItem; order: number; editable: boolean; onSelect: () => void }) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
    disabled: !editable,
  });

  const body = (
    <>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[15px] font-bold">{item.place.name}</span>
        <span className="block text-[12px] text-ink-sub">
          {categoryLabel(item.place.category)} · {item.place.region}
        </span>
      </span>
      {item.fixedTime ? (
        <span className="inline-flex shrink-0 items-center gap-1 rounded-md bg-selected px-2 py-1 text-[13px] font-bold text-primary-deep tabular-nums">
          <Clock3 size={13} /> {item.fixedTime}
        </span>
      ) : (
        editable && <span className="shrink-0 text-[12px] text-ink-faint">시간 없음</span>
      )}
      {editable && <ChevronRight size={16} className="shrink-0 text-ink-faint" />}
    </>
  );

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn("relative flex items-center gap-3", isDragging && "z-10 opacity-90")}
    >
      <span
        className={cn(
          "flex size-7 shrink-0 items-center justify-center rounded-full text-[12px] font-bold",
          item.visited ? "bg-selected text-primary-deep" : item.fixedTime ? "bg-primary-deep text-white" : "bg-primary text-on-primary",
        )}
        aria-label={item.visited ? `${order}번째 · 다녀옴` : `${order}번째`}
      >
        {item.visited ? <Check size={14} strokeWidth={3} /> : order}
      </span>
      <div
        className={cn(
          "flex min-w-0 flex-1 items-center rounded-card border bg-bg",
          isDragging ? "border-primary shadow-lg" : "border-line",
        )}
      >
        {editable ? (
          <button
            type="button"
            onClick={onSelect}
            aria-label={`${item.place.name}${item.fixedTime ? ` · ${item.fixedTime} 고정` : ""} · 고정 시간 설정`}
            className="flex min-w-0 flex-1 items-center gap-2 py-3 pl-4 text-left active:bg-fill"
          >
            {body}
          </button>
        ) : (
          <div className="flex min-w-0 flex-1 items-center gap-2 px-4 py-3">{body}</div>
        )}
        {editable && (
          <button
            type="button"
            ref={setActivatorNodeRef}
            {...attributes}
            {...listeners}
            aria-label={`${item.place.name} 순서 옮기기`}
            className="flex size-11 shrink-0 cursor-grab touch-none items-center justify-center text-ink-faint active:cursor-grabbing active:bg-fill"
          >
            <GripVertical size={20} />
          </button>
        )}
      </div>
    </div>
  );
}
