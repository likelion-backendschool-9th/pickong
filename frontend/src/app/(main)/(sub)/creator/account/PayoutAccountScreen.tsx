"use client";

import { Landmark } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { Field, Input } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useCreator, useSavePayoutAccount } from "../_api/creatorApi";
import { BANKS, accountDigits, accountNumberError } from "../_lib/creator";

export function PayoutAccountScreen() {
  const { data, isPending, isError, error } = useCreator();
  const router = useRouter();
  const toast = useToast();
  const save = useSavePayoutAccount();
  const [bank, setBank] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [holder, setHolder] = useState("");
  const [submitted, setSubmitted] = useState(false);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  if (data.status !== "APPROVED") {
    return (
      <Screen>
        <TopBar title="정산 계좌" backHref={routes.creator()} />
        <ErrorState message="크리에이터 승인 후 정산 계좌를 등록할 수 있어요." />
      </Screen>
    );
  }

  const numberError = submitted ? accountNumberError(accountNumber) : null;
  const submit = () => {
    setSubmitted(true);
    if (!bank || accountNumberError(accountNumber) || !holder.trim()) return;
    save.mutate(
      { bank, accountNumber: accountDigits(accountNumber), holder: holder.trim() },
      {
        onSuccess: () => {
          toast({ message: "정산 계좌를 저장했어요" });
          router.replace(routes.creator());
        },
      },
    );
  };

  return (
    <Screen>
      <TopBar title="정산 계좌" backHref={routes.creator()} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        {data.account && (
          <div className="flex items-center gap-3 rounded-card bg-soft px-4 py-4">
            <Landmark size={20} className="shrink-0 text-primary-deep" aria-hidden />
            <div>
              <p className="text-[12px] text-ink-sub">지금 등록된 계좌</p>
              <p className="text-[15px] font-bold tabular-nums">
                {data.account.bank} {data.account.accountNumberMasked}
              </p>
              <p className="text-[12px] text-ink-sub">예금주 {data.account.holder}</p>
            </div>
          </div>
        )}
        <div>
          <h2 className="text-[20px] font-bold">{data.account ? "새 계좌로 바꾸기" : "정산받을 계좌를 등록해 주세요"}</h2>
          <p className="mt-1.5 text-[13px] text-ink-sub">매달 말일까지의 수익을 다음 달 10일에 보내 드려요. 본인 명의 계좌만 등록할 수 있어요.</p>
        </div>

        <div>
          <p className="mb-2 text-[13px] font-bold">은행</p>
          <div className="flex flex-wrap gap-2">
            {BANKS.map((b) => (
              <Chip key={b} selected={bank === b} onClick={() => setBank(b)}>
                {b}
              </Chip>
            ))}
          </div>
          {submitted && !bank && <p className="mt-2 text-[12px] text-danger">은행을 골라 주세요.</p>}
        </div>

        <Field label="계좌번호" htmlFor="account-number" error={numberError} hint="- 없이 숫자만 입력해도 돼요">
          <Input
            id="account-number"
            inputMode="numeric"
            value={accountNumber}
            onChange={(e) => setAccountNumber(e.target.value.replace(/[^\d-]/g, ""))}
            placeholder="계좌번호"
            className="tabular-nums"
          />
        </Field>

        <Field label="예금주" htmlFor="account-holder" error={submitted && !holder.trim() ? "예금주를 입력해 주세요." : null}>
          <Input id="account-holder" value={holder} onChange={(e) => setHolder(e.target.value)} placeholder="실명" autoComplete="name" />
        </Field>

        {save.isError && <Notice tone="danger">{errorMessage(save.error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={save.isPending} onClick={submit}>
          {data.account ? "계좌 변경" : "계좌 등록"}
        </Button>
      </BottomCta>
    </Screen>
  );
}
