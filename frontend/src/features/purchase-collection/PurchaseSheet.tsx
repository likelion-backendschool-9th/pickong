"use client";

import { CreditCard, FlaskConical } from "lucide-react";
import { useState } from "react";
import { usePurchaseCollection, type Collection } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { formatWon } from "@/shared/lib/money";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { CheckMark } from "@/shared/ui/controls";
import { Notice } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";

/**
 * 유료 컬렉션 구매 (단건).
 * 지금은 테스트 결제만 연결되어 있어 실제 돈이 나가지 않습니다. PG(토스페이먼츠 등)를 붙일 때는
 * ‘결제하기’에서 결제창을 열고, 승인 결과를 서버에 넘기는 부분만 바꾸면 됩니다.
 */
export function PurchaseSheet({ open, onClose, collection }: { open: boolean; onClose: () => void; collection: Collection }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="컬렉션 구매">
      {open && <Checkout collection={collection} onDone={onClose} />}
    </BottomSheet>
  );
}

function Checkout({ collection: c, onDone }: { collection: Collection; onDone: () => void }) {
  const [agreed, setAgreed] = useState(false);
  const purchase = usePurchaseCollection(c.id);
  const toast = useToast();
  const price = c.price ?? 0;

  const pay = () =>
    purchase.mutate(
      { method: "TEST", amount: price },
      {
        onSuccess: (detail) => {
          toast({ message: "구매했어요", detail: `${detail.placeCount}곳을 모두 볼 수 있어요` });
          onDone();
        },
      },
    );

  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <div className="rounded-card bg-fill px-4 py-3">
        <p className="text-[15px] font-bold">{c.title}</p>
        <p className="mt-0.5 text-[12px] text-ink-sub">
          {c.owner.nickname} · 장소 {c.placeCount}곳 · 한 번 구매하면 계속 볼 수 있어요
        </p>
        <p className="mt-3 flex items-baseline justify-between border-t border-line pt-3">
          <span className="text-[13px] text-ink-sub">결제 금액</span>
          <span className="text-[20px] font-bold tabular-nums">{formatWon(price)}</span>
        </p>
      </div>

      <p className="mt-5 mb-2 text-[13px] font-bold">결제 수단</p>
      <div className="space-y-2" role="radiogroup" aria-label="결제 수단">
        <div role="radio" aria-checked="true" className="flex items-center gap-3 rounded-card border border-primary-deep/40 bg-accent-soft px-4 py-3">
          <FlaskConical size={18} className="text-primary-deep" />
          <span className="min-w-0 flex-1">
            <span className="block text-[14px] font-bold">테스트 결제</span>
            <span className="block text-[12px] text-ink-sub">개발용 · 실제로 돈이 나가지 않아요</span>
          </span>
        </div>
        <div role="radio" aria-checked="false" aria-disabled="true" className="flex items-center gap-3 rounded-card border border-line px-4 py-3 opacity-60">
          <CreditCard size={18} className="text-ink-sub" />
          <span className="min-w-0 flex-1">
            <span className="block text-[14px] font-bold">카드 · 간편결제</span>
            <span className="block text-[12px] text-ink-sub">결제 서비스 연결 후 제공돼요</span>
          </span>
        </div>
      </div>

      <button type="button" role="checkbox" aria-checked={agreed} onClick={() => setAgreed((v) => !v)} className="mt-4 flex w-full items-start gap-2.5 text-left">
        <CheckMark checked={agreed} />
        <span className="text-[12px] leading-relaxed text-ink-sub">
          구매 내용을 확인했고, 디지털 콘텐츠 특성상 장소 목록을 연 뒤에는 환불이 제한될 수 있음에 동의해요.
        </span>
      </button>

      {purchase.isError && (
        <Notice tone="danger" className="mt-3">
          {errorMessage(purchase.error)}
        </Notice>
      )}

      <Button block className="mt-4" disabled={!agreed} loading={purchase.isPending} onClick={pay}>
        {formatWon(price)} 테스트 결제하기
      </Button>
    </div>
  );
}
