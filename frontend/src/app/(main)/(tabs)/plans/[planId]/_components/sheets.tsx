"use client";

import { ArrowRight, Clock3, ListOrdered, MapPinOff, Pencil } from "lucide-react";
import { useMemo, useState } from "react";
import { categoryLabel, formatHours, useSavedPlaces } from "@/entities/place";
import { suggestForGap, suggestOrder, type Plan, type PlanItem, type PlanWarning, type Timeline } from "@/entities/plan";
import { cn } from "@/shared/lib/cn";
import { formatDuration, toHHmm } from "@/shared/lib/time";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Stepper } from "@/shared/ui/controls";
import { Notice } from "@/shared/ui/display";
import { describeWarning, targetItemId } from "../_lib/warningText";

/* ── 충돌 경고 ─────────────────────────────────────── */

export function ConflictSheet({
  target,
  items,
  onClose,
  onSuggest,
  onExclude,
  onStay,
}: {
  target: { item: PlanItem; warning: PlanWarning } | null;
  items: PlanItem[];
  onClose: () => void;
  onSuggest: () => void;
  onExclude: (itemId: string) => void;
  onStay: (itemId: string) => void;
}) {
  const d = target ? describeWarning(target.warning, target.item, items) : null;
  const fixId = target ? targetItemId(target.warning, target.item) : null;
  const fixItem = items.find((i) => i.id === fixId);
  return (
    <BottomSheet open={!!target} onClose={onClose} title={d?.title}>
      {d && (
        <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
          <Notice tone="warning">{d.detail}</Notice>
          <p className="mt-4 mb-2 text-[13px] font-bold">어떻게 해결할까요?</p>
          <div className="space-y-2">
            <Option icon={<Pencil size={18} />} title="직접 수정" desc="카드를 끌어서 순서를 바꿔요" onClick={onClose} />
            <Option icon={<ListOrdered size={18} />} title="다른 순서 추천" desc="확정 예약은 그대로 두고 순서를 제안해요" onClick={onSuggest} />
            {fixItem && (
              <>
                <Option
                  icon={<Clock3 size={18} />}
                  title="체류 시간 수정"
                  desc={`${fixItem.place.name} · 지금 ${fixItem.stayMinutes}분`}
                  onClick={() => onStay(fixItem.id)}
                />
                <Option
                  icon={<MapPinOff size={18} />}
                  title="장소 제외"
                  desc={`${fixItem.place.name}을(를) 이 일정에서 빼요`}
                  onClick={() => onExclude(fixItem.id)}
                />
              </>
            )}
          </div>
          <p className="mt-3 text-[12px] text-ink-faint">추천은 제안으로만 보여주고, 승인한 뒤에 적용해요.</p>
        </div>
      )}
    </BottomSheet>
  );
}

function Option({ icon, title, desc, onClick }: { icon: React.ReactNode; title: string; desc: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center gap-3 rounded-card border border-line px-4 py-3 text-left active:bg-fill">
      <span className="text-primary-deep">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[14px] font-bold">{title}</span>
        <span className="block text-[12px] text-ink-sub">{desc}</span>
      </span>
    </button>
  );
}

/* ── 다른 순서 추천 (승인 후 적용) ───────────────────── */

export function SuggestSheet({
  open,
  plan,
  items,
  current,
  onClose,
  onApply,
}: {
  open: boolean;
  plan: Plan;
  items: PlanItem[];
  current: Timeline;
  onClose: () => void;
  onApply: (items: PlanItem[]) => void;
}) {
  const suggestion = useMemo(() => (open ? suggestOrder(plan, items) : null), [open, plan, items]);
  return (
    <BottomSheet open={open} onClose={onClose} title="이 순서는 어때요?" description="확정 예약 시간은 그대로예요. 이동 시간은 예상값이에요.">
      {open && (
        <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
          {!suggestion ? (
            <Notice>더 나은 순서를 찾지 못했어요. 장소를 제외하거나 체류 시간을 줄여 보세요.</Notice>
          ) : (
            <>
              <div className="mb-3 flex items-center gap-2 text-[13px]">
                <span className="rounded-md bg-warning-soft px-2 py-1 font-bold text-warning">지금 경고 {current.warningCount}건</span>
                <ArrowRight size={14} className="text-ink-faint" />
                <span className="rounded-md bg-selected px-2 py-1 font-bold text-primary-deep">추천 경고 {suggestion.timeline.warningCount}건</span>
              </div>
              <ol className="space-y-1.5">
                {suggestion.items.map((it, i) => {
                  const moved = items[i]?.id !== it.id;
                  const e = suggestion.timeline.entries[i];
                  return (
                    <li key={it.id} className={cn("flex items-center gap-3 rounded-lg px-3 py-2", moved && "bg-soft")}>
                      <span className="w-5 text-center text-[12px] font-bold text-ink-sub">{i + 1}</span>
                      <span className="w-11 text-[13px] font-bold tabular-nums">{toHHmm(e.startAt)}</span>
                      <span className="min-w-0 flex-1 truncate text-[14px]">{it.place.name}</span>
                      {it.kind === "RESERVATION" && <span className="text-[11px] text-primary-deep">고정</span>}
                      {moved && it.kind === "FREE" && <span className="text-[11px] font-bold text-primary-deep">이동</span>}
                    </li>
                  );
                })}
              </ol>
            </>
          )}
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button disabled={!suggestion} onClick={() => suggestion && onApply(suggestion.items)}>
              이 순서로 적용
            </Button>
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

/* ── 체류 시간 수정 ───────────────────────────────── */

export function StaySheet({
  item,
  startAt,
  onClose,
  onApply,
}: {
  item: PlanItem | null;
  startAt: number;
  onClose: () => void;
  onApply: (minutes: number) => void;
}) {
  return (
    <BottomSheet open={!!item} onClose={onClose} title="체류 시간 수정" description={item?.place.name}>
      {item && <StayForm key={item.id} initial={item.stayMinutes} startAt={startAt} closeAt={item.place.businessHours?.close} onApply={onApply} />}
    </BottomSheet>
  );
}

function StayForm({ initial, startAt, closeAt, onApply }: { initial: number; startAt: number; closeAt?: string; onApply: (m: number) => void }) {
  const [minutes, setMinutes] = useState(initial);
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <div className="flex flex-col items-center gap-3 py-4">
        <Stepper label="체류 시간" value={minutes} onChange={setMinutes} step={10} min={10} max={360} format={formatDuration} />
        <p className="text-[13px] text-ink-sub tabular-nums">
          {toHHmm(startAt)} 도착 → {toHHmm(startAt + minutes)} 출발{closeAt && ` · 영업 종료 ${closeAt}`}
        </p>
      </div>
      <Button block onClick={() => onApply(minutes)}>
        적용
      </Button>
    </div>
  );
}

/* ── 빈 시간 장소 추천 ────────────────────────────── */

export function GapSheet({
  gap,
  plan,
  items,
  onClose,
  onAdd,
}: {
  gap: { index: number; startAt: number; endAt: number } | null;
  plan: Plan;
  items: PlanItem[];
  onClose: () => void;
  onAdd: (index: number, item: PlanItem) => void;
}) {
  const { data: saved = [] } = useSavedPlaces();
  const suggestions = useMemo(() => {
    if (!gap) return [];
    const inPlan = new Set(items.map((i) => i.place.id));
    return suggestForGap(
      saved.map((s) => s.place).filter((p) => !inPlan.has(p.id)),
      {
        from: gap.index === 0 ? plan.startPlace : items[gap.index - 1].place,
        to: items[gap.index].place,
        startAt: gap.startAt,
        endAt: gap.endAt,
        date: plan.date,
      },
    );
  }, [gap, saved, items, plan]);

  return (
    <BottomSheet
      open={!!gap}
      onClose={onClose}
      title={gap ? `${toHHmm(gap.startAt)} — ${toHHmm(gap.endAt)} 사이에 들를 곳` : ""}
      description={gap ? `다음 일정 ${items[gap.index].place.name} 전까지 · 이동 · 체류 40분 · 영업시간을 고려했어요` : undefined}
    >
      {gap && (
        <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
          {suggestions.length === 0 ? (
            <Notice>이 시간에 다녀올 수 있는 저장 장소가 없어요. 내 지도에 장소를 더 저장해 보세요.</Notice>
          ) : (
            <ul className="divide-y divide-line">
              {suggestions.map((s) => (
                <li key={s.place.id} className="flex items-center gap-3 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-[14px] font-bold">{s.place.name}</p>
                    <p className="text-[12px] text-ink-sub">
                      {categoryLabel(s.place.category)} · 이동 약 {s.travelFromPrev}분 · {toHHmm(s.arriveAt)} 도착 · {formatHours(s.place.businessHours)}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() =>
                      onAdd(gap.index, {
                        id: `tmp-${Date.now()}`,
                        kind: "FREE",
                        place: s.place,
                        stayMinutes: 40,
                        visited: false,
                        reservation: null,
                      })
                    }
                  >
                    추가
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </BottomSheet>
  );
}

/* ── 저장 장소에서 추가 ───────────────────────────── */

export function AddPlaceSheet({
  open,
  items,
  onClose,
  onAdd,
}: {
  open: boolean;
  items: PlanItem[];
  onClose: () => void;
  onAdd: (item: PlanItem) => void;
}) {
  const { data: saved = [] } = useSavedPlaces();
  const inPlan = new Set(items.map((i) => i.place.id));
  const candidates = saved.filter((s) => !inPlan.has(s.place.id));
  return (
    <BottomSheet open={open} onClose={onClose} title="내 지도에서 장소 추가" description="맨 뒤에 추가돼요. 추가한 뒤 순서를 옮길 수 있어요.">
      <ul className="divide-y divide-line pb-[calc(12px+env(safe-area-inset-bottom))]">
        {candidates.map((s) => (
          <li key={s.id} className="flex items-center gap-3 py-3">
            <div className="min-w-0 flex-1">
              <p className="text-[14px] font-bold">{s.place.name}</p>
              <p className="text-[12px] text-ink-sub">
                {s.place.region} · {categoryLabel(s.place.category)} · {formatHours(s.place.businessHours)}
              </p>
            </div>
            <Button
              size="sm"
              variant="secondary"
              onClick={() =>
                onAdd({ id: `tmp-${Date.now()}`, kind: "FREE", place: s.place, stayMinutes: 60, visited: false, reservation: null })
              }
            >
              추가
            </Button>
          </li>
        ))}
        {candidates.length === 0 && <p className="py-6 text-center text-[13px] text-ink-sub">추가할 수 있는 저장 장소가 없어요.</p>}
      </ul>
    </BottomSheet>
  );
}
