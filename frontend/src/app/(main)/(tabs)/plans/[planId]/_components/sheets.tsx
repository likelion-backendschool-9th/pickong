"use client";

import { MapPinOff } from "lucide-react";
import { useState } from "react";
import { categoryLabel, useSavedPlaces, type Place } from "@/entities/place";
import type { PlanItem } from "@/entities/plan";
import { PlaceSearchPanel } from "@/features/place-search";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Field, Input } from "@/shared/ui/form";

/* ── 장소 눌렀을 때: 고정 시간 설정 · 일정에서 빼기 ───────── */

export function ItemSheet({
  item,
  onClose,
  onSave,
  onRemove,
}: {
  item: PlanItem | null;
  onClose: () => void;
  onSave: (time: string | null) => void;
  onRemove: () => void;
}) {
  return (
    <BottomSheet
      open={!!item}
      onClose={onClose}
      title={item?.place.name}
      description={item ? `${categoryLabel(item.place.category)} · ${item.place.region}` : undefined}
    >
      {item && <ItemForm key={item.id} initial={item.fixedTime} onSave={onSave} onRemove={onRemove} />}
    </BottomSheet>
  );
}

function ItemForm({ initial, onSave, onRemove }: { initial: string | null; onSave: (t: string | null) => void; onRemove: () => void }) {
  const [time, setTime] = useState(initial ?? "");
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <Field label="고정 시간" htmlFor="fixed-time" hint="영화 · 식당 예약처럼 시간이 정해진 곳만 적어요. 카페처럼 자유로운 곳은 비워 두세요.">
        <Input id="fixed-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
      </Field>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Button variant="outline" disabled={!initial && !time} onClick={() => onSave(null)}>
          시간 없음
        </Button>
        <Button disabled={!time || time === initial} onClick={() => onSave(time)}>
          {time ? `${time}로 고정` : "시간을 골라 주세요"}
        </Button>
      </div>
      <button
        type="button"
        onClick={onRemove}
        className="mt-3 flex h-11 w-full items-center justify-center gap-1.5 text-[13px] font-bold text-danger"
      >
        <MapPinOff size={16} /> 일정에서 빼기
      </button>
    </div>
  );
}

/* ── 장소 추가: 내 지도에 저장한 곳 + 검색 ─────────────── */

export function AddPlaceSheet({
  open,
  items,
  onClose,
  onAdd,
}: {
  open: boolean;
  items: PlanItem[];
  onClose: () => void;
  onAdd: (place: Place) => void;
}) {
  const { data: saved = [] } = useSavedPlaces();
  const inPlan = new Set(items.map((i) => i.place.id));
  const recent = saved
    .filter((s) => !inPlan.has(s.place.id))
    .map((s) => ({ place: s.place, note: `${s.place.region} · ${categoryLabel(s.place.category)}` }));
  return (
    <BottomSheet open={open} onClose={onClose} title="장소 추가" description="맨 뒤에 추가돼요. 추가한 뒤 순서를 옮길 수 있어요." className="h-[85dvh]">
      {open && <PlaceSearchPanel autoFocus recentTitle="내가 저장한 장소" recent={recent} onSelect={onAdd} />}
    </BottomSheet>
  );
}
