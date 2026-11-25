"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { CheckMark, RadioMark } from "@/shared/ui/controls";
import { Notice } from "@/shared/ui/display";
import { Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { cn } from "@/shared/lib/cn";
import { useWithdraw } from "../_api/accountApi";
import { withdrawAction } from "./_lib/withdraw";

const REASONS = [
  { value: "RARELY_USED", label: "자주 쓰지 않아요" },
  { value: "MISSING_FEATURE", label: "원하는 기능이 없어요" },
  { value: "PRIVACY", label: "개인정보가 걱정돼요" },
  { value: "OTHER_SERVICE", label: "다른 서비스를 쓸래요" },
  { value: "ETC", label: "기타" },
];

/**
 * 탈퇴 안내 (backend_blueprint.md '회원 탈퇴 결정'과 같은 내용).
 * 결제 기록 보관 · 크리에이터 정산 안내는 그 기능이 생길 때 약관과 함께 넣습니다(아직 없는 기능을 약속하지 않기 위해).
 */
const LOSSES = [
  "탈퇴하면 바로 모든 기기에서 로그아웃되고, 프로필과 게시물이 다른 사람에게 보이지 않아요.",
  "30일 안에 같은 소셜 계정으로 다시 로그인하면 탈퇴를 취소할 수 있어요.",
  "30일이 지나면 이름 · 이메일 · 사진 같은 정보와 게시물, 저장한 장소 · 컬렉션 · 일정이 지워지고 되돌릴 수 없어요.",
  "남긴 댓글은 지워지지 않고 작성자가 '탈퇴한 사용자'로 보여요.",
];

export function WithdrawScreen() {
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();
  const withdraw = useWithdraw();
  const [reason, setReason] = useState("");
  const [detail, setDetail] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  /** 확인 체크 없이 탈퇴하기를 누른 횟수. 바뀔 때마다 체크 칸을 다시 흔듭니다(0이면 안내하지 않음) */
  const [nudge, setNudge] = useState(0);
  const confirmRef = useRef<HTMLButtonElement>(null);

  const action = withdrawAction(reason, detail, confirmed);
  const showConfirmHint = nudge > 0 && !confirmed;

  // 체크 없이 눌렀으면 체크 칸이 보이게 스크롤하고 키보드 · 화면 낭독기가 바로 체크할 수 있게 포커스를 옮깁니다.
  useEffect(() => {
    if (nudge === 0) return;
    confirmRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
    confirmRef.current?.focus({ preventScroll: true });
  }, [nudge]);

  const submit = () => {
    if (action === "NEEDS_CONFIRM") {
      setNudge((n) => n + 1);
      return;
    }
    withdraw.mutate(
      { reason, detail },
      {
        onSuccess: () => {
          // 서버가 세션 쿠키를 지웠으므로 남은 내 정보 캐시도 비웁니다.
          qc.clear();
          toast({ message: "탈퇴를 신청했어요. 30일 안에 다시 로그인하면 취소할 수 있어요." });
          router.replace(routes.start());
        },
      },
    );
  };

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

        {/* key가 바뀌면 다시 그려져 흔들림이 처음부터 다시 재생됩니다 */}
        <div key={nudge} className={cn("mt-6", showConfirmHint && "motion-safe:animate-shake-y")}>
          <button
            ref={confirmRef}
            type="button"
            role="checkbox"
            aria-checked={confirmed}
            aria-invalid={showConfirmHint || undefined}
            aria-describedby={showConfirmHint ? "withdraw-confirm-hint" : undefined}
            onClick={() => setConfirmed((v) => !v)}
            className={cn(
              "-mx-3 flex w-[calc(100%+24px)] items-start gap-2.5 rounded-card px-3 py-2.5 text-left transition-colors",
              showConfirmHint && "bg-danger-soft",
            )}
          >
            <CheckMark checked={confirmed} />
            <span className={cn("text-[13px] leading-relaxed", showConfirmHint ? "text-danger" : "text-ink-sub")}>
              위 내용을 모두 확인했고, 30일이 지나면 되돌릴 수 없다는 데 동의해요.
            </span>
          </button>
          {showConfirmHint && (
            <p id="withdraw-confirm-hint" role="alert" className="mt-1.5 text-[13px] font-bold text-danger">
              탈퇴하려면 위 내용을 확인하고 체크해 주세요.
            </p>
          )}
        </div>

        {withdraw.isError && <Notice tone="danger" className="mt-4">{errorMessage(withdraw.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          variant="danger"
          disabled={action === "DISABLED"}
          loading={withdraw.isPending}
          onClick={submit}
        >
          탈퇴하기
        </Button>
      </BottomCta>
    </Screen>
  );
}
