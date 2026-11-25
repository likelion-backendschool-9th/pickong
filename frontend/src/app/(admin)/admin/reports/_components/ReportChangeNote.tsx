"use client";

import { useState } from "react";
import type { Report } from "../../_api/adminApi";
import { reportBodyNeedsToggle, reportChangeLabel } from "../_lib/reportTargetPreview";

/** 수정된 신고의 당시 본문과 현재 본문. 카드 제목과 처리 버튼은 바꾸지 않습니다. */
export function ReportChangeNote({ report }: { report: Report }) {
  const [open, setOpen] = useState(false);
  if (report.target.modified !== true) return null;

  const snapshotText = report.target.snapshot?.text ?? null;
  const currentText = report.target.current?.text ?? null;
  const canToggle =
    (snapshotText != null && snapshotText !== "" && reportBodyNeedsToggle(snapshotText)) ||
    (currentText != null && currentText !== "" && reportBodyNeedsToggle(currentText));
  const clamped = canToggle && !open;

  return (
    <div className="mt-1 min-w-0">
      <p className="text-[12px] font-bold text-ink-sub">{reportChangeLabel(report.targetType)}</p>
      <BodyLine label="신고 당시" text={snapshotText} emptyLabel="신고 당시 내용 없음" clamped={clamped} />
      <BodyLine label="현재" text={currentText} emptyLabel="현재 내용 없음" clamped={clamped} />
      {canToggle && (
        <button
          type="button"
          className="mt-1 text-[12px] font-bold text-ink-sub"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? "접기" : "더 보기"}
        </button>
      )}
    </div>
  );
}

function BodyLine({
  label,
  text,
  emptyLabel,
  clamped,
}: {
  label: string;
  text: string | null;
  emptyLabel: string;
  clamped: boolean;
}) {
  const empty = text == null || text === "";
  return (
    <p className={`mt-1 break-words text-[12px] text-ink-sub ${clamped ? "line-clamp-2 whitespace-pre-wrap" : "whitespace-pre-wrap"}`}>
      <span className="font-bold">{label}: </span>
      {empty ? emptyLabel : text}
    </p>
  );
}
