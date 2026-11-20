"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { Button } from "@/shared/ui/Button";
import { RadioMark } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton, Thumb } from "@/shared/ui/display";
import { Field, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { usePurchase, useRequestRefund } from "../../_api/purchaseApi";
import { REFUND_REASONS, refundDaysLeft } from "../../_lib/refund";

export function RefundScreen({ purchaseId }: { purchaseId: string }) {
  const { data: p, isPending, isError, error } = usePurchase(purchaseId);
  const refund = useRequestRefund(purchaseId);
  const router = useRouter();
  const toast = useToast();
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  if (!p.refundable) {
    return (
      <Screen>
        <TopBar title="환불 신청" backHref={routes.purchases()} />
        <ErrorState message={p.status !== "PAID" ? "이미 환불을 신청한 구매예요." : "구매 후 7일이 지나 환불을 신청할 수 없어요."} />
      </Screen>
    );
  }

  const needDetail = reason === "ETC" || reason === "NOT_AS_DESCRIBED";
  const ready = !!reason && (!needDetail || !!detail.trim());

  return (
    <Screen>
      <TopBar title="환불 신청" backHref={routes.purchases()} />
      <div className="px-5 pt-2 pb-6">
        {p.collection && (
          <div className="flex gap-3 rounded-card bg-fill p-3">
            <Thumb src={p.collection.coverImageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-[15px] font-bold">{p.collection.title}</p>
              <p className="mt-0.5 text-[12px] text-ink-sub">
                {formatDotDate(p.purchasedAt)} 결제 · {formatWon(p.amount)}
              </p>
            </div>
          </div>
        )}
        <p className="mt-3 text-[12px] text-ink-sub">
          {formatDotDate(p.refundDeadline)}까지 신청할 수 있어요 ({refundDaysLeft(p.refundDeadline)}일 남음)
        </p>

        <fieldset className="mt-6">
          <legend className="mb-2 text-[13px] font-bold">환불 사유</legend>
          <div role="radiogroup">
            {REFUND_REASONS.map((r) => (
              <button
                key={r.value}
                type="button"
                role="radio"
                aria-checked={reason === r.value}
                onClick={() => setReason(r.value)}
                className="flex w-full items-center gap-3 py-2.5 text-left text-[14px]"
              >
                <RadioMark checked={reason === r.value} />
                {r.label}
              </button>
            ))}
          </div>
        </fieldset>

        <Field label="자세한 내용" htmlFor="refund-detail" optional={!needDetail} className="mt-4" hint={`${detail.length}/300`}>
          <Textarea
            id="refund-detail"
            value={detail}
            maxLength={300}
            onChange={(e) => setDetail(e.target.value)}
            placeholder={needDetail ? "어떤 점이 달랐는지 적어 주시면 빠르게 확인할게요." : "남기고 싶은 말이 있다면 적어 주세요."}
          />
        </Field>

        <Notice tone="info" className="mt-5">
          운영팀이 확인한 뒤 영업일 기준 3일 안에 결과를 알려 드려요. 승인되면 카드 결제가 취소되고, 컬렉션 열람이 막혀요.
        </Notice>
        {refund.isError && <Notice tone="danger" className="mt-3">{errorMessage(refund.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!ready}
          loading={refund.isPending}
          onClick={() =>
            refund.mutate(
              { reason, detail: detail.trim() },
              {
                onSuccess: () => {
                  toast({ message: "환불을 신청했어요", detail: "결과는 구매 내역에서 확인할 수 있어요" });
                  router.replace(routes.purchases());
                },
              },
            )
          }
        >
          {formatWon(p.amount)} 환불 신청
        </Button>
      </BottomCta>
    </Screen>
  );
}
