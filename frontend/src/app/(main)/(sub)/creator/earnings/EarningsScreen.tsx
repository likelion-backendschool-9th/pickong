"use client";

import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { Tabs } from "@/shared/ui/controls";
import { Badge, EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useCreator, useEarnings } from "../_api/creatorApi";

const SETTLEMENT_LABEL = {
  SCHEDULED: { label: "지급 예정", tone: "warning" },
  PAID: { label: "지급 완료", tone: "lime" },
  HELD: { label: "지급 보류", tone: "danger" },
} as const;

/** 판매 내역(건별) · 정산 내역(월별) */
export function EarningsScreen() {
  const { data: creator } = useCreator();
  const approved = creator?.status === "APPROVED";
  const { data, isPending, isError, error, refetch } = useEarnings(approved);
  const [tab, setTab] = useState<"sales" | "settlements">("sales");

  return (
    <div className="pb-10">
      <TopBar title="수익 내역" backHref={routes.creator()} />
      {creator && !approved ? (
        <ErrorState message="크리에이터 승인 후 볼 수 있어요." />
      ) : isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <>
          <div className="mx-5 mt-1 rounded-card bg-soft px-5 py-4">
            <p className="text-[12px] text-ink-sub">다음 정산 예정 금액</p>
            <p className="mt-1 text-[26px] font-bold tabular-nums">{formatWon(data.summary.scheduled)}</p>
            <p className="mt-1 text-[12px] text-ink-sub">
              아직 정산 전 {formatWon(data.summary.unsettled)} · 수수료 {Math.round(data.summary.feeRate * 100)}%
            </p>
          </div>
          <div className="mt-4 border-b border-line px-5">
            <Tabs
              items={[
                { value: "sales", label: `판매 ${data.sales.length}` },
                { value: "settlements", label: `정산 ${data.settlements.length}` },
              ]}
              value={tab}
              onChange={setTab}
            />
          </div>
          {tab === "sales" ? (
            data.sales.length === 0 ? (
              <EmptyState title="아직 판매 내역이 없어요" description="전체 공개 컬렉션에 가격을 정하면 판매가 시작돼요." />
            ) : (
              <ul className="divide-y divide-line px-5">
                {data.sales.map((s) => (
                  <li key={s.id} className="flex items-start gap-3 py-3.5">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[14px] font-bold">{s.collectionTitle}</p>
                      <p className="mt-0.5 text-[12px] text-ink-sub">
                        {s.buyerUsername} · {formatDotDate(s.soldAt)}
                      </p>
                    </div>
                    <div className="text-right">
                      {s.status === "REFUNDED" ? (
                        <Badge tone="neutral">환불됨</Badge>
                      ) : (
                        <>
                          <p className="text-[14px] font-bold tabular-nums">+{formatWon(s.net)}</p>
                          <p className="text-[11px] text-ink-faint tabular-nums">
                            {formatWon(s.amount)} − 수수료 {formatWon(s.fee)}
                          </p>
                        </>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )
          ) : data.settlements.length === 0 ? (
            <EmptyState title="아직 정산 내역이 없어요" description="매달 말일까지의 수익을 다음 달 10일에 보내 드려요." />
          ) : (
            <ul className="divide-y divide-line px-5">
              {data.settlements.map((s) => (
                <li key={s.id} className="flex items-center gap-3 py-3.5">
                  <div className="min-w-0 flex-1">
                    <p className="text-[14px] font-bold">{s.period.replace("-", "년 ")}월 정산</p>
                    <p className="mt-0.5 text-[12px] text-ink-sub tabular-nums">
                      판매 {formatWon(s.salesAmount)} − 수수료 {formatWon(s.fee)}
                      {s.paidAt && ` · ${formatDotDate(s.paidAt)} 지급`}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-[15px] font-bold tabular-nums">{formatWon(s.amount)}</p>
                    <Badge tone={SETTLEMENT_LABEL[s.status].tone} className="mt-1">
                      {SETTLEMENT_LABEL[s.status].label}
                    </Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
