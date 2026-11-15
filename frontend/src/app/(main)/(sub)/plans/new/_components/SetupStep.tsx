"use client";

import { useState } from "react";
import type { Place } from "@/entities/place";
import { PlaceSearchPanel } from "@/features/place-search";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Field, Input, SelectTrigger, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { routes } from "@/shared/config/routes";

export interface PlanBasics {
  title: string;
  date: string;
  region: string;
  startPlace: Place | null;
  startTime: string;
  endTime: string;
  memo: string;
}

export function SetupStep({ value, onChange, onNext }: { value: PlanBasics; onChange: (v: PlanBasics) => void; onNext: () => void }) {
  const [startOpen, setStartOpen] = useState(false);
  const set = (patch: Partial<PlanBasics>) => onChange({ ...value, ...patch });
  const valid = !!value.date && !!value.startPlace && value.endTime > value.startTime;

  return (
    <Screen>
      <TopBar title="새 일정 만들기" backHref={routes.plans()} />
      <div className="space-y-5 px-5 pt-2 pb-6">
        <p className="text-[12px] font-bold text-primary-deep">1 / 3 · 날짜와 지역</p>
        <Field label="일정 제목" htmlFor="p-title" optional>
          <Input id="p-title" value={value.title} onChange={(e) => set({ title: e.target.value })} placeholder={`${value.region || "성수"}에서 보내는 토요일`} maxLength={40} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="날짜" htmlFor="p-date">
            <Input id="p-date" type="date" value={value.date} onChange={(e) => set({ date: e.target.value })} />
          </Field>
          <Field label="지역" htmlFor="p-region">
            <Input id="p-region" value={value.region} onChange={(e) => set({ region: e.target.value })} placeholder="성수동" />
          </Field>
        </div>
        <Field label="출발 위치" htmlFor="p-start">
          <SelectTrigger id="p-start" value={value.startPlace?.name ?? "출발 위치 선택"} onClick={() => setStartOpen(true)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="출발 시간" htmlFor="p-st">
            <Input id="p-st" type="time" value={value.startTime} onChange={(e) => set({ startTime: e.target.value })} />
          </Field>
          <Field label="종료 희망 시간" htmlFor="p-et" error={value.endTime <= value.startTime ? "출발 시간 이후로 정해 주세요" : undefined}>
            <Input id="p-et" type="time" value={value.endTime} onChange={(e) => set({ endTime: e.target.value })} />
          </Field>
        </div>
        <Field label="동행자 · 선호 메모" htmlFor="p-memo" optional>
          <Textarea id="p-memo" value={value.memo} onChange={(e) => set({ memo: e.target.value })} placeholder="예: 친구 1명 · 많이 걷지 않는 동선" className="min-h-20" maxLength={100} />
        </Field>
      </div>
      <BottomCta>
        <Button block disabled={!valid} onClick={onNext}>
          다음 · 확정 예약 등록
        </Button>
      </BottomCta>
      <BottomSheet open={startOpen} onClose={() => setStartOpen(false)} title="출발 위치" className="h-[85dvh]">
        {startOpen && (
          <PlaceSearchPanel
            autoFocus
            placeholder="역, 장소 이름 검색"
            onSelect={(p) => {
              set({ startPlace: p });
              setStartOpen(false);
            }}
          />
        )}
      </BottomSheet>
    </Screen>
  );
}
