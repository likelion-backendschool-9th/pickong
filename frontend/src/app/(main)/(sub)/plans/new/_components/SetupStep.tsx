"use client";

import { Button } from "@/shared/ui/Button";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { routes } from "@/shared/config/routes";

export interface PlanBasics {
  title: string;
  date: string;
  region: string;
  memo: string;
}

/** 1단계: 날짜 · 지역 (출발 · 종료 시간은 정하지 않습니다) */
export function SetupStep({ value, onChange, onNext }: { value: PlanBasics; onChange: (v: PlanBasics) => void; onNext: () => void }) {
  const set = (patch: Partial<PlanBasics>) => onChange({ ...value, ...patch });

  return (
    <Screen>
      <TopBar title="새 일정 만들기" backHref={routes.plans()} />
      <div className="space-y-5 px-5 pt-2 pb-6">
        <p className="text-[12px] font-bold text-primary-deep">1 / 2 · 날짜와 지역</p>
        <Field label="일정 제목" htmlFor="p-title" optional>
          <Input
            id="p-title"
            value={value.title}
            onChange={(e) => set({ title: e.target.value })}
            placeholder={`${value.region || "성수"}에서 보내는 하루`}
            maxLength={40}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="날짜" htmlFor="p-date">
            <Input id="p-date" type="date" value={value.date} onChange={(e) => set({ date: e.target.value })} />
          </Field>
          <Field label="지역" htmlFor="p-region" optional>
            <Input id="p-region" value={value.region} onChange={(e) => set({ region: e.target.value })} placeholder="성수동" />
          </Field>
        </div>
        <Field label="동행자 · 메모" htmlFor="p-memo" optional>
          <Textarea
            id="p-memo"
            value={value.memo}
            onChange={(e) => set({ memo: e.target.value })}
            placeholder="예: 친구 1명 · 많이 걷지 않는 동선"
            className="min-h-20"
            maxLength={100}
          />
        </Field>
      </div>
      <BottomCta>
        <Button block disabled={!value.date} onClick={onNext}>
          다음 · 장소 고르기
        </Button>
      </BottomCta>
    </Screen>
  );
}
