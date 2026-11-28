"use client";

import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { exceptionDraftsFromInputs, toAdminExceptionInputs, type ExceptionHourDraft, type ExceptionHourStatus } from "../_lib/hoursDraft";
import type { AdminPlaceExceptionInput } from "../_lib/models";

const empty = (): ExceptionHourDraft => ({ exceptionDate: "", status: "closed", open: "10:00", close: "18:00", overnight: false, note: "" });

export function HoursExceptionEditor({
  seed = [],
  onChange,
  error,
}: {
  seed?: readonly AdminPlaceExceptionInput[];
  onChange: (rows: AdminPlaceExceptionInput[]) => void;
  error?: string;
}) {
  const [rows, setRows] = useState(() => exceptionDraftsFromInputs(seed));

  const update = (next: ExceptionHourDraft[]) => {
    setRows(next);
    onChange(toAdminExceptionInputs(next));
  };

  return (
    <section id="admin-place-exceptions" tabIndex={-1} className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-[15px] font-bold">예외 영업시간 <span className="font-normal text-ink-sub">선택</span></h2>
          <p className="mt-1 text-[13px] text-ink-sub">특정 날짜만 다르게 열 때 사용해요.</p>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={() => update([...rows, empty()])}>
          날짜 추가
        </Button>
      </div>
      <ul className="space-y-2">
        {rows.map((row, index) => (
          <li key={index} className="space-y-2 rounded-card border border-line p-3">
            <div className="flex flex-wrap gap-2">
              <label className="text-[12px] text-ink-sub">
                날짜
                <input
                  type="date"
                  aria-label={`예외 날짜 ${index + 1}`}
                  value={row.exceptionDate}
                  onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, exceptionDate: event.target.value } : item)))}
                  className="mt-1 block h-10 rounded-control border border-line bg-bg px-2 text-[13px] text-ink"
                />
              </label>
              <label className="min-w-0 flex-1 text-[12px] text-ink-sub">
                상태
                <select
                  aria-label={`예외 상태 ${index + 1}`}
                  value={row.status}
                  onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, status: event.target.value as ExceptionHourStatus, overnight: false } : item)))}
                  className="mt-1 block h-10 w-full rounded-control border border-line bg-bg px-2 text-[13px]"
                >
                  <option value="closed">휴무</option>
                  <option value="open">영업</option>
                  <option value="allDay">24시간</option>
                </select>
              </label>
            </div>
            {row.status === "open" && (
              <div className="flex flex-wrap items-center gap-2">
                <input type="time" aria-label={`예외 시작 ${index + 1}`} value={row.open} onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, open: event.target.value } : item)))} className="h-10 rounded-control border border-line bg-bg px-2 text-[13px]" />
                <input type="time" aria-label={`예외 종료 ${index + 1}`} value={row.close} onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, close: event.target.value } : item)))} className="h-10 rounded-control border border-line bg-bg px-2 text-[13px]" />
                <label className="inline-flex items-center gap-1.5 text-[13px]">
                  <input type="checkbox" checked={row.overnight} onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, overnight: event.target.checked } : item)))} />
                  익일 종료
                </label>
              </div>
            )}
            <label className="block text-[12px] text-ink-sub">
              메모
              <input
                aria-label={`예외 메모 ${index + 1}`}
                value={row.note}
                onChange={(event) => update(rows.map((item, itemIndex) => (itemIndex === index ? { ...item, note: event.target.value } : item)))}
                className="mt-1 h-10 w-full rounded-control border border-line bg-bg px-3 text-[13px] text-ink"
              />
            </label>
            <Button type="button" variant="ghost" size="sm" onClick={() => update(rows.filter((_, itemIndex) => itemIndex !== index))}>
              이 날짜 삭제
            </Button>
          </li>
        ))}
      </ul>
      {error && (
        <p className="text-[12px] text-danger" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
