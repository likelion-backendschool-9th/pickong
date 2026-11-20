"use client";

import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { Button } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { Badge, EmptyState, ErrorState, Notice, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useProcessRefund, useRefundRequests, type RefundRequest } from "../_api/adminApi";
import { ReasonSheet } from "../_components/ReasonSheet";

const REASON: Record<string, string> = {
  WRONG_PURCHASE: "실수로 잘못 구매",
  NOT_AS_DESCRIBED: "소개와 내용이 다름",
  CLOSED_PLACES: "폐업한 장소가 많음",
  DUPLICATE: "중복 결제",
  ETC: "기타",
};

export function RefundsScreen() {
  const [status, setStatus] = useState<"PENDING" | "DONE">("PENDING");
  const { data, isPending, isError, error, refetch } = useRefundRequests(status);
  const process = useProcessRefund();
  const toast = useToast();
  const [rejecting, setRejecting] = useState<RefundRequest | null>(null);

  return (
    <div className="pb-10">
      <TopBar title="환불 처리" backHref={routes.admin()} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "PENDING", label: "대기" },
            { value: "DONE", label: "처리 완료" },
          ]}
          value={status}
          onChange={setStatus}
        />
      </div>
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title={status === "PENDING" ? "처리할 환불 신청이 없어요" : "처리한 환불이 없어요"} />
      ) : (
        <ul className="space-y-3 px-5 pt-4">
          {data.map((p) => (
            <li key={p.id} className="rounded-card border border-line p-4">
              <div className="flex gap-3">
                <Thumb src={p.collection?.coverImageUrl ?? null} alt="" className="size-14" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[14px] font-bold">{p.collection?.title ?? "삭제된 컬렉션"}</p>
                  <p className="mt-0.5 text-[12px] text-ink-sub">
                    구매자 {p.buyer?.nickname ?? "알 수 없음"} · 판매자 {p.collection?.ownerNickname}
                  </p>
                  <p className="mt-0.5 text-[12px] text-ink-sub">
                    {formatDotDate(p.purchasedAt)} · {p.method === "CARD" ? p.card?.company : "테스트 결제"}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-[15px] font-bold tabular-nums">{formatWon(p.amount)}</p>
                  {p.status === "REFUNDED" && <Badge tone="neutral">환불 완료</Badge>}
                  {p.status === "REFUND_REJECTED" && <Badge tone="danger">거절</Badge>}
                </div>
              </div>
              {p.refund && (
                <div className="mt-3 rounded-lg bg-fill px-3 py-2.5 text-[13px]">
                  <p className="font-bold">{REASON[p.refund.reason] ?? p.refund.reason}</p>
                  {p.refund.detail && <p className="mt-0.5 text-ink-sub">“{p.refund.detail}”</p>}
                  <p className="mt-1 text-[11px] text-ink-faint">{formatDotDate(p.refund.requestedAt)} 신청</p>
                  {p.refund.rejectReason && <p className="mt-1 text-[12px] text-danger">거절 사유: {p.refund.rejectReason}</p>}
                </div>
              )}
              {p.status === "REFUND_REQUESTED" && (
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button size="md" variant="outline" onClick={() => setRejecting(p)}>
                    거절
                  </Button>
                  <Button
                    size="md"
                    loading={process.isPending && process.variables?.id === p.id}
                    onClick={() =>
                      process.mutate(
                        { id: p.id, approve: true, rejectReason: "" },
                        { onSuccess: () => toast({ message: `${formatWon(p.amount)} 환불을 승인했어요`, detail: "카드 결제가 취소되고 열람 권한이 회수돼요" }) },
                      )
                    }
                  >
                    승인 · 결제 취소
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {process.isError && !rejecting && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(process.error)}</Notice>}

      <ReasonSheet
        open={!!rejecting}
        title="환불 거절"
        description="구매자에게 거절 사유가 안내돼요."
        placeholder="거절 사유 (예: 장소 목록을 모두 열람해 환불 대상이 아니에요)"
        confirmLabel="거절"
        danger
        loading={process.isPending}
        error={process.isError ? process.error : null}
        onClose={() => {
          setRejecting(null);
          process.reset();
        }}
        onConfirm={(reason) =>
          rejecting &&
          process.mutate(
            { id: rejecting.id, approve: false, rejectReason: reason },
            {
              onSuccess: () => {
                toast({ message: "환불 신청을 거절했어요" });
                setRejecting(null);
              },
            },
          )
        }
      />
    </div>
  );
}
