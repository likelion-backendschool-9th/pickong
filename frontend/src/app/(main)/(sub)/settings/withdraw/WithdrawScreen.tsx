"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { CheckMark, RadioMark } from "@/shared/ui/controls";
import { Notice } from "@/shared/ui/display";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useWithdraw } from "../_api/accountApi";

const REASONS = [
  { value: "RARELY_USED", label: "자주 쓰지 않아요" },
  { value: "MISSING_FEATURE", label: "원하는 기능이 없어요" },
  { value: "PRIVACY", label: "개인정보가 걱정돼요" },
  { value: "OTHER_SERVICE", label: "다른 서비스를 쓸래요" },
  { value: "ETC", label: "기타" },
];

const LOSSES = [
  "저장한 장소 · 컬렉션 · 일정 · 게시물이 모두 지워져요.",
  "구매한 유료 컬렉션도 다시 볼 수 없어요. 결제 기록은 법령에 따라 5년간 보관돼요.",
  "크리에이터라면 정산 전 수익은 탈퇴 후 다음 정산일에 지급돼요.",
  "탈퇴 후 30일 동안은 같은 이메일로 다시 가입할 수 없어요.",
];

export function WithdrawScreen() {
  const router = useRouter();
  const toast = useToast();
  const withdraw = useWithdraw();
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmed, setConfirmed] = useState(false);

  const ready = !!reason && !!password && confirmed && (reason !== "ETC" || !!detail.trim());

  return (
    <Screen>
      <TopBar title="회원 탈퇴" backHref={routes.settings()} />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[22px] leading-snug font-bold">탈퇴하기 전에 확인해 주세요</h2>
        <ul className="mt-4 space-y-2 rounded-card bg-danger-soft px-4 py-4">
          {LOSSES.map((l) => (
            <li key={l} className="flex gap-2 text-[13px] leading-relaxed text-ink">
              <span className="mt-[7px] size-1 shrink-0 rounded-full bg-danger" aria-hidden />
              {l}
            </li>
          ))}
        </ul>

        <fieldset className="mt-7">
          <legend className="mb-2 text-[13px] font-bold">떠나시는 이유를 알려 주세요</legend>
          <div className="space-y-1" role="radiogroup">
            {REASONS.map((r) => (
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
          {reason === "ETC" && (
            <Textarea
              aria-label="기타 사유"
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              placeholder="불편했던 점을 적어 주시면 더 나은 찜콩을 만드는 데 쓸게요."
              className="mt-2"
              maxLength={300}
            />
          )}
        </fieldset>

        <Field label="비밀번호 확인" htmlFor="withdraw-password" className="mt-6">
          <Input
            id="withdraw-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="본인 확인을 위해 비밀번호를 입력해 주세요"
          />
        </Field>

        <button
          type="button"
          role="checkbox"
          aria-checked={confirmed}
          onClick={() => setConfirmed((v) => !v)}
          className="mt-6 flex w-full items-start gap-2.5 text-left"
        >
          <CheckMark checked={confirmed} />
          <span className="text-[13px] leading-relaxed text-ink-sub">위 내용을 모두 확인했고, 탈퇴하면 데이터를 되돌릴 수 없다는 데 동의해요.</span>
        </button>

        {withdraw.isError && <Notice tone="danger" className="mt-4">{errorMessage(withdraw.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          variant="danger"
          disabled={!ready}
          loading={withdraw.isPending}
          onClick={() =>
            withdraw.mutate(
              { reason, detail, password },
              {
                onSuccess: () => {
                  toast({ message: "탈퇴가 완료됐어요. 그동안 고마웠어요." });
                  router.replace(routes.start());
                },
              },
            )
          }
        >
          탈퇴하기
        </Button>
      </BottomCta>
    </Screen>
  );
}
