"use client";

import { useState } from "react";
import { DAY_LABELS } from "../_lib/businessHours";
import { dayDraftsFromInputs, toAdminHourInputs, type DayHourDraft, type DayHourStatus } from "../_lib/hoursDraft";
import type { AdminPlaceHourInput } from "../_lib/models";

const STATUS: { value: DayHourStatus; label: string }[] = [
  { value: "unset", label: "미등록" },
  { value: "open", label: "영업" },
  { value: "closed", label: "휴무" },
  { value: "allDay", label: "24시간" },
];

export function BusinessHoursEditor({
  seed = [],
  onChange,
  error,
}: {
  seed?: readonly AdminPlaceHourInput[];
  onChange: (rows: AdminPlaceHourInput[]) => void;
  error?: string;
}) {
  const [days, setDays] = useState(() => dayDraftsFromInputs(seed));

  const update = (next: DayHourDraft[]) => {
    setDays(next);
    onChange(toAdminHourInputs(next));
  };

  return (
    <section id="admin-place-hours" tabIndex={-1} className="space-y-3">
      <details open className="space-y-3">
      <summary className="cursor-pointer list-none text-[15px] font-bold [&::-webkit-details-marker]:hidden">영업시간 <span className="font-normal text-ink-sub">선택</span></summary>
      <p className="text-[13px] text-ink-sub">미등록 요일은 저장하지 않고, 휴무와는 다르게 둡니다. 접어도 입력한 시간은 유지돼요.</p>
      <ul className="space-y-2">
        {days.map((day, index) => (
          <li key={day.dayOfWeek} className="rounded-card border border-line p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="w-6 text-[14px] font-bold">{DAY_LABELS[day.dayOfWeek]}</span>
              <label className="sr-only" htmlFor={`admin-hours-${day.dayOfWeek}`}>
                {DAY_LABELS[day.dayOfWeek]}요일 영업
              </label>
              <select
                id={`admin-hours-${day.dayOfWeek}`}
                value={day.status}
                onChange={(event) => {
                  const status = event.target.value as DayHourStatus;
                  const next = days.map((item, itemIndex) => (itemIndex === index ? { ...item, status, overnight: status === "open" ? item.overnight : false } : item));
                  update(next);
                }}
                className="h-10 min-w-0 flex-1 rounded-control border border-line bg-bg px-2 text-[13px]"
              >
                {STATUS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            {day.status === "open" && (
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <TimeField label={`${DAY_LABELS[day.dayOfWeek]} 시작`} value={day.open} onChange={(open) => update(days.map((item, itemIndex) => (itemIndex === index ? { ...item, open } : item)))} />
                <TimeField label={`${DAY_LABELS[day.dayOfWeek]} 종료`} value={day.close} onChange={(close) => update(days.map((item, itemIndex) => (itemIndex === index ? { ...item, close } : item)))} />
                <label className="inline-flex h-10 items-center gap-1.5 text-[13px]">
                  <input
                    type="checkbox"
                    checked={day.overnight}
                    onChange={(event) => update(days.map((item, itemIndex) => (itemIndex === index ? { ...item, overnight: event.target.checked } : item)))}
                  />
                  익일 종료
                </label>
              </div>
            )}
            {day.status === "closed" && <p className="mt-2 text-[12px] text-ink-sub">휴무로 저장해요. 00:00~00:00 영업이 아닙니다.</p>}
            {day.status === "allDay" && <p className="mt-2 text-[12px] text-ink-sub">24시간 영업으로 저장해요.</p>}
          </li>
        ))}
      </ul>
      {error && (
        <p className="text-[12px] text-danger" role="alert">
          {error}
        </p>
      )}
      </details>
    </section>
  );
}

function TimeField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="text-[12px] text-ink-sub">
      <span className="sr-only">{label}</span>
      <input type="time" aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} className="h-10 rounded-control border border-line bg-bg px-2 text-[13px] text-ink" />
    </label>
  );
}
