"use client";

import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import type { Place } from "@/entities/place";
import { insertReservation, RESERVATION_TYPE_LABEL, ReservationCard, removeItem, type PlanItem, type ReservationType } from "@/entities/plan";
import { PlaceSearchPanel } from "@/features/place-search";
import { formatLongDate } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Chip, Stepper } from "@/shared/ui/controls";
import { EmptyState, Notice } from "@/shared/ui/display";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";

/** 외부 확정 예약 등록: 다른 서비스에서 이미 확정한 예약을 기록합니다 (예약·결제 기능 아님). */
export function ReservationsStep({
  date,
  reservations,
  onChange,
  onBack,
  onNext,
}: {
  date: string;
  reservations: PlanItem[];
  onChange: (items: PlanItem[]) => void;
  onBack: () => void;
  onNext: () => void;
}) {
  const [formOpen, setFormOpen] = useState(false);
  return (
    <Screen>
      <TopBar title="확정 예약 등록" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <p className="text-[12px] font-bold text-primary-deep">2 / 3 · 확정 예약</p>
        <h2 className="mt-2 text-[20px] font-bold">이미 확정한 예약이 있나요?</h2>
        <p className="mt-1 text-[13px] text-ink-sub">{formatLongDate(date)} · 예약 시간은 일정에서 고정돼요.</p>
        <Notice className="mt-4">다른 서비스에서 확정한 예약만 기록해요. 찜콩에서 예약이나 결제를 진행하지 않아요.</Notice>

        {reservations.length === 0 ? (
          <EmptyState title="등록한 예약이 없어요" description="예약 없이 자유 일정만으로도 만들 수 있어요." className="py-8" />
        ) : (
          <ul className="mt-4 space-y-2">
            {reservations.map((r) => (
              <li key={r.id} className="flex items-center gap-2">
                <ReservationCard item={r} className="min-w-0 flex-1" />
                <button type="button" aria-label={`${r.place.name} 예약 삭제`} onClick={() => onChange(removeItem(reservations, r.id))} className="flex size-10 items-center justify-center text-ink-faint">
                  <Trash2 size={18} />
                </button>
              </li>
            ))}
          </ul>
        )}
        <Button variant="secondary" block className="mt-4" icon={<Plus size={18} />} onClick={() => setFormOpen(true)}>
          확정 예약 추가
        </Button>
      </div>
      <BottomCta>
        <Button block onClick={onNext}>
          {reservations.length ? `다음 · 방문 후보 선택` : "예약 없이 계속"}
        </Button>
      </BottomCta>
      <BottomSheet open={formOpen} onClose={() => setFormOpen(false)} title="확정 예약 추가" className="h-[92dvh]">
        {formOpen && (
          <ReservationForm
            onAdd={(item) => {
              onChange(insertReservation(reservations, item));
              setFormOpen(false);
            }}
          />
        )}
      </BottomSheet>
    </Screen>
  );
}

function ReservationForm({ onAdd }: { onAdd: (item: PlanItem) => void }) {
  const [type, setType] = useState<ReservationType>("RESTAURANT");
  const [place, setPlace] = useState<Place | null>(null);
  const [startTime, setStart] = useState("12:30");
  const [endTime, setEnd] = useState("13:40");
  const [people, setPeople] = useState(2);
  const [reservationNumber, setNumber] = useState("");
  const [link, setLink] = useState("");
  const [memo, setMemo] = useState("");
  const timeError = endTime <= startTime ? "종료 시간은 시작 시간 이후여야 해요" : undefined;

  return (
    <div className="space-y-5 pb-[calc(12px+env(safe-area-inset-bottom))]">
      <Field label="예약 유형">
        <div className="flex flex-wrap gap-1.5">
          {(Object.keys(RESERVATION_TYPE_LABEL) as ReservationType[]).map((t) => (
            <Chip key={t} selected={type === t} onClick={() => setType(t)}>
              {RESERVATION_TYPE_LABEL[t]}
            </Chip>
          ))}
        </div>
      </Field>
      <Field label="장소">
        {place ? (
          <div className="flex items-center justify-between rounded-control bg-soft px-4 py-3">
            <span className="text-[14px] font-bold">{place.name}</span>
            <button type="button" onClick={() => setPlace(null)} className="text-[13px] font-bold text-primary-deep">
              변경
            </button>
          </div>
        ) : (
          <PlaceSearchPanel placeholder="예약한 장소 검색 (예: 식당 A, 영화관 B)" onSelect={setPlace} />
        )}
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="시작" htmlFor="r-start">
          <Input id="r-start" type="time" value={startTime} onChange={(e) => setStart(e.target.value)} />
        </Field>
        <Field label="종료" htmlFor="r-end" error={timeError}>
          <Input id="r-end" type="time" value={endTime} onChange={(e) => setEnd(e.target.value)} />
        </Field>
      </div>
      <Field label="인원">
        <Stepper label="인원" value={people} onChange={setPeople} step={1} min={1} max={20} format={(v) => `${v}명`} />
      </Field>
      <Field label="예약번호" htmlFor="r-no" optional>
        <Input id="r-no" value={reservationNumber} onChange={(e) => setNumber(e.target.value)} placeholder="A-2391" />
      </Field>
      <Field label="외부 예약 링크" htmlFor="r-link" optional>
        <Input id="r-link" type="url" value={link} onChange={(e) => setLink(e.target.value)} placeholder="https://" />
      </Field>
      <Field label="메모" htmlFor="r-memo" optional>
        <Textarea id="r-memo" value={memo} onChange={(e) => setMemo(e.target.value)} className="min-h-16" placeholder="창가 자리 요청" />
      </Field>
      <Button
        block
        disabled={!place || !!timeError}
        onClick={() =>
          place &&
          onAdd({
            id: `tmp-r${Date.now()}`,
            kind: "RESERVATION",
            place,
            stayMinutes: 0,
            visited: false,
            reservation: { type, startTime, endTime, people, reservationNumber, link, memo },
          })
        }
      >
        고정 일정으로 추가
      </Button>
    </div>
  );
}
