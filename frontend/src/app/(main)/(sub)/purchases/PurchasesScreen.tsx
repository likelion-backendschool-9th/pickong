"use client";

import { ReceiptText } from "lucide-react";
import Link from "next/link";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { Badge, EmptyState, ErrorState, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { usePurchases, type Purchase } from "./_api/purchaseApi";
import { PURCHASE_STATUS, refundDaysLeft, refundReasonLabel } from "./_lib/refund";

export function PurchasesScreen() {
  const { data, isPending, isError, error, refetch } = usePurchases();

  return (
    <div className="pb-10">
      <TopBar title="구매 내역" backHref={routes.settings()} />
      {isPending ? (
        <Skeleton className="m-5 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState icon={<ReceiptText size={24} />} title="구매한 컬렉션이 없어요" description="크리에이터의 유료 컬렉션을 사면 여기에서 확인할 수 있어요." />
      ) : (
        <ul className="space-y-3 px-5 pt-1">
          {data.map((p) => (
            <li key={p.id}>
              <PurchaseCard p={p} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PurchaseCard({ p }: { p: Purchase }) {
  const status = PURCHASE_STATUS[p.status];
  const daysLeft = refundDaysLeft(p.refundDeadline);
  const method = p.method === "TEST" ? "테스트 결제" : `${p.card?.company ?? "카드"}${p.card?.installment ? ` · ${p.card.installment}개월` : " · 일시불"}`;

  return (
    <div className="rounded-card border border-line p-4">
      <div className="flex items-center justify-between text-[12px] text-ink-sub">
        <span>{formatDotDate(p.purchasedAt)}</span>
        <Badge tone={status.tone}>{status.label}</Badge>
      </div>
      {p.collection ? (
        <Link href={routes.collection(p.collection.id)} className="mt-3 flex gap-3">
          <Thumb src={p.collection.coverImageUrl} alt="" className="size-14" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-[15px] font-bold">{p.collection.title}</p>
            <p className="mt-0.5 text-[12px] text-ink-sub">{p.collection.ownerNickname}</p>
          </div>
        </Link>
      ) : (
        <p className="mt-3 text-[14px] text-ink-faint">삭제된 컬렉션</p>
      )}
      <div className="mt-3 flex items-baseline justify-between border-t border-line pt-3">
        <span className="text-[12px] text-ink-sub">{method}</span>
        <span className="text-[16px] font-bold tabular-nums">{formatWon(p.amount)}</span>
      </div>

      {p.refund && (
        <div className="mt-3 rounded-lg bg-fill px-3 py-2.5 text-[12px] leading-relaxed text-ink-sub">
          <p>
            환불 사유: {refundReasonLabel(p.refund.reason)} · {formatDotDate(p.refund.requestedAt)} 신청
          </p>
          {p.status === "REFUNDED" && p.refund.processedAt && <p>{formatDotDate(p.refund.processedAt)}에 카드 결제를 취소했어요. 카드사에 따라 3~5일 걸릴 수 있어요.</p>}
          {p.status === "REFUND_REJECTED" && <p className="text-danger">거절 사유: {p.refund.rejectReason}</p>}
        </div>
      )}

      {p.refundable && (
        <Link
          href={routes.refund(p.id)}
          className="mt-3 flex h-10 items-center justify-center rounded-control border border-line text-[13px] font-medium"
        >
          환불 신청 <span className="ml-1 text-ink-faint">· {daysLeft}일 남음</span>
        </Link>
      )}
    </div>
  );
}
