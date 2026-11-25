"use client";

import { useState } from "react";
import type { Report } from "../../_api/adminApi";
import { reportChangeLabel } from "../_lib/reportTargetPreview";

/** 신고 당시 본문 열람과, 변경된 경우에만 현재 본문 비교. 카드 제목과 처리 버튼은 바꾸지 않습니다. */
export function ReportChangeNote({ report }: { report: Report }) {
  const [open, setOpen] = useState(false);
  const snapshotText = report.target.snapshot?.text;
  const hasSnapshot = report.target.snapshot != null && snapshotText != null;
  const changed = report.target.modified === true;
  const currentText = report.target.current?.text;

  if (!hasSnapshot && !changed) return null;

  return (
    <div className="mt-1 min-w-0">
      {hasSnapshot && (
        <div className="min-w-0">
          <button
            type="button"
            className="text-[12px] font-bold text-ink-sub"
            aria-expanded={open}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? "신고 당시 본문 접기" : "신고 당시 전체 본문 보기"}
          </button>
          {open && (
            <div className="mt-1 min-w-0">
              <p className="text-[12px] font-bold text-ink-sub">신고 당시 본문</p>
              <p className="mt-1 wrap-anywhere whitespace-pre-wrap text-[12px] text-ink-sub">
                {snapshotText === "" ? "신고 당시 내용 없음" : snapshotText}
              </p>
            </div>
          )}
        </div>
      )}
      {changed && (
        <div className="mt-1 min-w-0">
          <p className="text-[12px] font-bold text-ink-sub">{reportChangeLabel(report.targetType)}</p>
          {currentText != null && (
            <p className="mt-1 wrap-anywhere whitespace-pre-wrap text-[12px] text-ink-sub">
              <span className="font-bold">현재: </span>
              {currentText === "" ? "현재 내용 없음" : currentText}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
