"use client";

import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { Button } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { Avatar, Badge, EmptyState, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useSettlementAction, useSettlements } from "../_api/adminApi";

const periodLabel = (period: string) => `${period.replace("-", "년 ")}월`;

export function SettlementsScreen() {
  const [status, setStatus] = useState<"SCHEDULED" | "PAID">("SCHEDULED");
  const { data, isPending, isError, error, refetch } = useSettlements(status);
  const action = useSettlementAction();
  const toast = useToast();
  const total = (data ?? []).filter((s) => s.status === "SCHEDULED").reduce((n, s) => n + s.amount, 0);

  return (
    <div className="pb-10">
      <TopBar title="정산 처리" backHref={routes.admin()} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "SCHEDULED", label: "지급 예정" },
            { value: "PAID", label: "지급 완료" },
          ]}
          value={status}
          onChange={setStatus}
        />
      </div>
      {status === "SCHEDULED" && data && data.length > 0 && (
        <div className="mx-5 mt-4 rounded-card bg-soft px-4 py-3">
          <p className="text-[12px] text-ink-sub">이번 지급 예정 합계 (보류 제외)</p>
          <p className="text-[22px] font-bold tabular-nums">{formatWon(total)}</p>
        </div>
      )}
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title={status === "SCHEDULED" ? "지급할 정산이 없어요" : "지급한 정산이 없어요"} />
      ) : (
        <ul className="space-y-3 px-5 pt-4">
          {data.map((s) => {
            const busy = action.isPending && action.variables?.id === s.id;
            return (
              <li key={s.id} className="rounded-card border border-line p-4">
                <div className="flex items-center gap-3">
                  <Avatar src={s.creator?.profileImageUrl} name={s.creator?.nickname ?? "?"} size={36} />
                  <div className="min-w-0 flex-1">
                    <p className="text-[14px] font-bold">{s.creator?.nickname}</p>
                    <p className="text-[12px] text-ink-sub">{periodLabel(s.period)} 정산</p>
                  </div>
                  {s.status === "HELD" && <Badge tone="danger">보류</Badge>}
                  {s.status === "PAID" && <Badge>지급 완료</Badge>}
                </div>
                <dl className="mt-3 grid grid-cols-3 gap-2 rounded-lg bg-fill px-3 py-2.5 text-center text-[12px]">
                  <div>
                    <dt className="text-ink-faint">판매</dt>
                    <dd className="font-bold tabular-nums">{formatWon(s.salesAmount)}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-faint">수수료</dt>
                    <dd className="font-bold tabular-nums">−{formatWon(s.fee)}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-faint">지급액</dt>
                    <dd className="font-bold text-primary-deep tabular-nums">{formatWon(s.amount)}</dd>
                  </div>
                </dl>
                <p className="mt-2 text-[12px] text-ink-sub">
                  {s.account ? `${s.account.bank} ${s.account.accountNumberMasked} · ${s.account.holder}` : "⚠ 정산 계좌가 없어요"}
                  {s.paidAt && ` · ${formatDotDate(s.paidAt)} 지급`}
                </p>
                {s.status !== "PAID" && (
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    {s.status === "HELD" ? (
                      <Button size="md" variant="outline" loading={busy} onClick={() => action.mutate({ id: s.id, action: "RELEASE" })}>
                        보류 해제
                      </Button>
                    ) : (
                      <Button size="md" variant="outline" loading={busy} onClick={() => action.mutate({ id: s.id, action: "HOLD" }, { onSuccess: () => toast({ message: "정산을 보류했어요" }) })}>
                        보류
                      </Button>
                    )}
                    <Button
                      size="md"
                      disabled={!s.account || s.status === "HELD"}
                      loading={busy}
                      onClick={() =>
                        action.mutate(
                          { id: s.id, action: "PAY" },
                          { onSuccess: () => toast({ message: `${s.creator?.nickname}님에게 ${formatWon(s.amount)} 지급 처리했어요` }) },
                        )
                      }
                    >
                      지급 완료
                    </Button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {action.isError && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(action.error)}</Notice>}
    </div>
  );
}
