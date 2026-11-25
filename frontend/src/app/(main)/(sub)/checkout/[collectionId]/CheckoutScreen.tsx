"use client";

import { CheckCircle2, CreditCard, Lock, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { useCollection, type Collection } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatWon } from "@/shared/lib/money";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { CheckMark } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton, Thumb } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useConfirmPayment, useCreateOrder, type PaymentOrder } from "./_api/paymentApi";
import { installmentLabel, installmentOptions } from "./_lib/installment";

const FALLBACK_COMPANIES = ["신한카드", "삼성카드", "현대카드", "KB국민카드", "롯데카드", "하나카드", "우리카드", "BC카드", "NH농협카드"];

export function CheckoutScreen({ collectionId }: { collectionId: string }) {
  const { data, isPending, isError, error } = useCollection(collectionId);
  // 결제가 끝나면 컬렉션이 ‘구매함’으로 다시 조회되므로, 완료 화면은 조회 결과와 상관없이 이 상태로 보여 줍니다.
  const [paid, setPaid] = useState<Collection | null>(null);

  if (paid) return <PaidScreen collection={paid} />;
  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  if (!data.price || data.isMine || !data.isLocked) {
    return (
      <Screen>
        <TopBar title="결제" backHref={routes.collection(collectionId)} />
        <ErrorState message={data.isPurchased ? "이미 구매한 컬렉션이에요." : "결제할 수 없는 컬렉션이에요."} />
        <div className="px-5">
          <ButtonLink href={routes.collection(collectionId)} variant="outline" block>
            컬렉션으로 돌아가기
          </ButtonLink>
        </div>
      </Screen>
    );
  }
  return <Checkout collection={data} onPaid={() => setPaid(data)} />;
}

function Checkout({ collection: c, onPaid }: { collection: Collection; onPaid: () => void }) {
  const price = c.price ?? 0;
  const [company, setCompany] = useState<string | null>(null);
  const [installment, setInstallment] = useState(0);
  const [agreed, setAgreed] = useState(false);
  const [order, setOrder] = useState<PaymentOrder | null>(null);
  const createOrder = useCreateOrder();
  const confirm = useConfirmPayment(c.id);
  const companies = order?.cardCompanies ?? FALLBACK_COMPANIES;

  return (
    <Screen>
      <TopBar title="결제" backHref={routes.collection(c.id)} />
      <div className="px-5 pt-2 pb-6">
        <section className="flex gap-3 rounded-card bg-fill p-3">
          <Thumb src={c.coverImageUrl} alt="" className="size-16" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-[15px] font-bold">{c.title}</p>
            <p className="mt-0.5 text-[12px] text-ink-sub">
              {c.owner.username} · 장소 {c.placeCount}곳
            </p>
            <p className="mt-1 text-[12px] text-ink-faint">한 번 구매하면 계속 볼 수 있어요</p>
          </div>
        </section>

        <section className="mt-6">
          <p className="mb-2 flex items-center gap-1.5 text-[13px] font-bold">
            <CreditCard size={15} className="text-primary-deep" /> 카드 선택
          </p>
          <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="카드사">
            {companies.map((name) => (
              <button
                key={name}
                type="button"
                role="radio"
                aria-checked={company === name}
                onClick={() => setCompany(name)}
                className={cn(
                  "h-12 rounded-control border text-[13px] transition-colors",
                  company === name ? "border-primary-deep/50 bg-selected font-bold text-primary-deep" : "border-line text-ink",
                )}
              >
                {name}
              </button>
            ))}
          </div>
        </section>

        <section className="mt-6">
          <p className="mb-2 text-[13px] font-bold">할부</p>
          <div className="flex gap-2">
            {installmentOptions(price).map((m) => (
              <button
                key={m}
                type="button"
                aria-pressed={installment === m}
                onClick={() => setInstallment(m)}
                className={cn(
                  "h-10 rounded-full border px-4 text-[13px]",
                  installment === m ? "border-primary-deep/50 bg-selected font-bold text-primary-deep" : "border-line text-ink-sub",
                )}
              >
                {installmentLabel(m)}
              </button>
            ))}
          </div>
          {price < 50_000 && <p className="mt-2 text-[12px] text-ink-faint">5만 원 이상 결제부터 할부를 고를 수 있어요.</p>}
        </section>

        <section className="mt-6 rounded-card border border-line px-4 py-3">
          <Row label="컬렉션 금액" value={formatWon(price)} />
          <Row label="할인" value="0원" />
          <div className="mt-2 flex items-baseline justify-between border-t border-line pt-3">
            <span className="text-[14px] font-bold">최종 결제 금액</span>
            <span className="text-[22px] font-bold tabular-nums">{formatWon(price)}</span>
          </div>
        </section>

        <button type="button" role="checkbox" aria-checked={agreed} onClick={() => setAgreed((v) => !v)} className="mt-5 flex w-full items-start gap-2.5 text-left">
          <CheckMark checked={agreed} />
          <span className="text-[12px] leading-relaxed text-ink-sub">
            주문 내용을 확인했어요. 구매 후 7일 안에는 환불을 신청할 수 있고, 디지털 콘텐츠라 장소 목록을 연 뒤에는 환불이 제한될 수 있어요.
          </span>
        </button>

        <p className="mt-4 flex items-center gap-1.5 text-[12px] text-ink-faint">
          <Lock size={13} aria-hidden /> 카드 번호는 결제 대행사 결제창에서만 입력해요. 찜콩은 카드 정보를 저장하지 않아요.
        </p>

        {createOrder.isError && <Notice tone="danger" className="mt-4">{errorMessage(createOrder.error)}</Notice>}
      </div>

      <BottomCta>
        <Button
          block
          disabled={!company || !agreed}
          loading={createOrder.isPending}
          onClick={() => createOrder.mutate(c.id, { onSuccess: setOrder })}
        >
          {formatWon(price)} 결제하기
        </Button>
      </BottomCta>

      <BottomSheet open={!!order} onClose={() => !confirm.isPending && setOrder(null)} title="카드 결제">
        {order && company && (
          <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
            <div className="rounded-card bg-fill px-4 py-4 text-center">
              <ShieldCheck size={28} className="mx-auto text-primary-deep" aria-hidden />
              <p className="mt-2 text-[13px] text-ink-sub">
                {company} · {installmentLabel(installment)}
              </p>
              <p className="mt-1 text-[24px] font-bold tabular-nums">{formatWon(order.amount)}</p>
              <p className="mt-1 text-[12px] text-ink-faint">{order.orderName}</p>
            </div>
            <Notice tone="info" className="mt-3">
              개발용 결제창이에요. 실제 결제 대행사(PG)를 연결하면 이 자리에 카드사 인증 화면이 열려요. 실제로 돈이 나가지 않아요.
            </Notice>
            {confirm.isError && <Notice tone="danger" className="mt-3">{errorMessage(confirm.error)}</Notice>}
            <Button
              block
              className="mt-4"
              loading={confirm.isPending}
              onClick={() =>
                confirm.mutate(
                  { orderId: order.orderId, amount: order.amount, cardCompany: company, installment },
                  {
                    onSuccess: () => {
                      setOrder(null);
                      onPaid();
                    },
                  },
                )
              }
            >
              카드 인증하고 결제
            </Button>
          </div>
        )}
      </BottomSheet>
    </Screen>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <p className="flex justify-between py-1 text-[13px]">
      <span className="text-ink-sub">{label}</span>
      <span className="tabular-nums">{value}</span>
    </p>
  );
}

function PaidScreen({ collection: c }: { collection: Collection }) {
  return (
    <Screen>
      <TopBar title="결제 완료" hideBack />
      <div className="flex flex-1 flex-col items-center px-5 pt-16 text-center">
        <div className="flex size-20 items-center justify-center rounded-full bg-soft">
          <CheckCircle2 size={38} className="text-primary-deep" aria-hidden />
        </div>
        <h2 className="mt-6 text-[22px] font-bold">결제가 완료됐어요</h2>
        <p className="mt-2 text-[14px] text-ink-sub">
          {c.title}의 장소 {c.placeCount}곳을 모두 볼 수 있어요.
        </p>
      </div>
      <BottomCta className="space-y-2">
        <ButtonLink href={routes.collection(c.id)} block replace>
          컬렉션 보러 가기
        </ButtonLink>
        <ButtonLink href={routes.purchases()} variant="secondary" block replace>
          구매 내역
        </ButtonLink>
      </BottomCta>
    </Screen>
  );
}
