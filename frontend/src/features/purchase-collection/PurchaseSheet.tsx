"use client";

import { CreditCard, FlaskConical } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { usePurchaseCollection, type Collection } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatWon } from "@/shared/lib/money";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { CheckMark } from "@/shared/ui/controls";
import { Notice } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";

/**
 * 유료 컬렉션 구매 (단건).
 * - 테스트 결제: 이 시트에서 바로 처리 (개발용, 실제 돈이 나가지 않음)
 * - 카드 결제: 결제 화면(/checkout/[collectionId])으로 이동해 카드사 · 할부를 고르고 결제창으로 승인합니다.
 */
export function PurchaseSheet({ open, onClose, collection }: { open: boolean; onClose: () => void; collection: Collection }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="컬렉션 구매">
      {open && <Checkout collection={collection} onDone={onClose} />}
    </BottomSheet>
  );
}

function Checkout({ collection: c, onDone }: { collection: Collection; onDone: () => void }) {
  const router = useRouter();
  const [method, setMethod] = useState<"TEST" | "CARD">("TEST");
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
          {c.owner.username} · 장소 {c.placeCount}곳 · 한 번 구매하면 계속 볼 수 있어요
        </p>
        <p className="mt-3 flex items-baseline justify-between border-t border-line pt-3">
          <span className="text-[13px] text-ink-sub">결제 금액</span>
          <span className="text-[20px] font-bold tabular-nums">{formatWon(price)}</span>
        </p>
      </div>

      <p className="mt-5 mb-2 text-[13px] font-bold">결제 수단</p>
      <div className="space-y-2" role="radiogroup" aria-label="결제 수단">
        <button
          type="button"
          role="radio"
          aria-checked={method === "TEST"}
          onClick={() => setMethod("TEST")}
          className={cn("flex w-full items-center gap-3 rounded-card border px-4 py-3 text-left", method === "TEST" ? "border-primary-deep/40 bg-accent-soft" : "border-line")}
        >
          <FlaskConical size={18} className={method === "TEST" ? "text-primary-deep" : "text-ink-sub"} />
          <span className="min-w-0 flex-1">
            <span className="block text-[14px] font-bold">테스트 결제</span>
            <span className="block text-[12px] text-ink-sub">개발용 · 실제로 돈이 나가지 않아요</span>
          </span>
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={method === "CARD"}
          onClick={() => setMethod("CARD")}
          className={cn("flex w-full items-center gap-3 rounded-card border px-4 py-3 text-left", method === "CARD" ? "border-primary-deep/40 bg-accent-soft" : "border-line")}
        >
          <CreditCard size={18} className={method === "CARD" ? "text-primary-deep" : "text-ink-sub"} />
          <span className="min-w-0 flex-1">
            <span className="block text-[14px] font-bold">카드 결제</span>
            <span className="block text-[12px] text-ink-sub">결제 화면에서 카드사 · 할부를 골라요</span>
          </span>
        </button>
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

      {method === "CARD" ? (
        <Button
          block
          className="mt-4"
          disabled={!agreed}
          onClick={() => {
            onDone();
            router.push(routes.checkout(c.id));
          }}
        >
          카드 결제 화면으로
        </Button>
      ) : (
        <Button block className="mt-4" disabled={!agreed} loading={purchase.isPending} onClick={pay}>
          {formatWon(price)} 테스트 결제하기
        </Button>
      )}
    </div>
  );
}
