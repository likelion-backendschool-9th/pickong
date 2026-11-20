"use client";

import { BadgeCheck, ChevronRight, Clock, Landmark, ReceiptText, Sparkles, XCircle } from "lucide-react";
import Link from "next/link";
import { useEffect, type ReactNode } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { formatWon } from "@/shared/lib/money";
import { ButtonLink } from "@/shared/ui/Button";
import { Badge, ErrorState, SectionTitle, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useCreator, useEarnings, useRefreshMeOnApproval, type CreatorApplication } from "./_api/creatorApi";
import { CREATOR_CATEGORIES } from "./_lib/creator";

const STATUS_LABEL: Record<CreatorApplication["status"], { label: string; tone: "lime" | "warning" | "danger" }> = {
  PENDING: { label: "심사 중", tone: "warning" },
  APPROVED: { label: "승인", tone: "lime" },
  REJECTED: { label: "반려", tone: "danger" },
};

const categoryLabel = (c: string) => CREATOR_CATEGORIES.find((x) => x.value === c)?.label ?? c;

/** 크리에이터 홈: 상태별로 신청 안내 · 심사 중 · 반려 사유 · 승인 후 수익 요약을 보여 줍니다. */
export function CreatorScreen() {
  const { data, isPending, isError, error, refetch } = useCreator();
  const approved = data?.status === "APPROVED";
  const { data: earnings } = useEarnings(approved);
  const refreshMe = useRefreshMeOnApproval();

  useEffect(() => {
    if (approved) refreshMe();
  }, [approved, refreshMe]);

  return (
    <div className="pb-10">
      <TopBar title="크리에이터" backHref={routes.settings()} />
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <div className="px-5 pt-2">
          {data.status === "NONE" && (
            <section className="rounded-card bg-soft px-5 py-6">
              <Sparkles size={26} className="text-primary-deep" aria-hidden />
              <h2 className="mt-3 text-[22px] leading-snug font-bold">나만의 장소 지도로<br />수익을 만들어 보세요</h2>
              <ul className="mt-4 space-y-1.5 text-[13px] text-ink-sub">
                <li>· 전체 공개 컬렉션에 500~50,000원 가격을 정할 수 있어요</li>
                <li>· 판매 금액에서 수수료 20%를 뺀 금액을 매달 정산해 드려요</li>
                <li>· 프로필에 크리에이터 배지가 붙어요</li>
              </ul>
              <ButtonLink href={routes.creatorApply()} block className="mt-6">
                크리에이터 신청하기
              </ButtonLink>
            </section>
          )}

          {data.status === "PENDING" && data.application && (
            <section className="rounded-card bg-warning-soft px-5 py-5">
              <div className="flex items-center gap-2 text-warning">
                <Clock size={20} aria-hidden />
                <h2 className="text-[17px] font-bold">심사 중이에요</h2>
              </div>
              <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">
                {formatDotDate(data.application.submittedAt)}에 신청했어요. 보통 영업일 기준 3일 안에 결과를 알려 드려요.
              </p>
            </section>
          )}

          {data.status === "REJECTED" && data.application && (
            <section className="rounded-card bg-danger-soft px-5 py-5">
              <div className="flex items-center gap-2 text-danger">
                <XCircle size={20} aria-hidden />
                <h2 className="text-[17px] font-bold">이번에는 함께하지 못하게 됐어요</h2>
              </div>
              <p className="mt-3 text-[13px] font-bold">반려 사유</p>
              <p className="mt-1 text-[13px] leading-relaxed text-ink-sub">{data.application.rejectReason}</p>
              <ButtonLink href={routes.creatorApply()} variant="outline" block className="mt-5">
                내용 보완해서 다시 신청하기
              </ButtonLink>
            </section>
          )}

          {approved && (
            <>
              <section className="flex items-center gap-3 rounded-card bg-soft px-5 py-4">
                <BadgeCheck size={26} className="shrink-0 text-primary-deep" aria-hidden />
                <div>
                  <h2 className="text-[16px] font-bold">크리에이터로 활동 중이에요</h2>
                  <p className="text-[12px] text-ink-sub">전체 공개 컬렉션에 가격을 정해 판매할 수 있어요</p>
                </div>
              </section>

              {earnings && (
                <section className="mt-5 grid grid-cols-2 gap-2">
                  <Stat label="정산 예정" value={formatWon(earnings.summary.scheduled)} strong />
                  <Stat label="아직 정산 전" value={formatWon(earnings.summary.unsettled)} />
                  <Stat label="누적 판매" value={formatWon(earnings.summary.totalSales)} />
                  <Stat label="지급 완료" value={formatWon(earnings.summary.paidTotal)} />
                </section>
              )}

              <ul className="mt-5 divide-y divide-line rounded-card border border-line">
                <MenuRow href={routes.creatorEarnings()} icon={<ReceiptText size={18} />} label="수익 내역" desc="판매 · 정산 내역" />
                <MenuRow
                  href={routes.creatorAccount()}
                  icon={<Landmark size={18} />}
                  label="정산 계좌"
                  desc={data.account ? `${data.account.bank} ${data.account.accountNumberMasked}` : "계좌를 등록해야 정산받을 수 있어요"}
                  warn={!data.account}
                />
              </ul>
            </>
          )}

          {data.history.length > 0 && (
            <section className="mt-8">
              <SectionTitle className="mb-2">심사 결과</SectionTitle>
              <ul className="space-y-2">
                {data.history.map((a) => (
                  <li key={a.id} className="rounded-card border border-line px-4 py-3">
                    <div className="flex items-center justify-between">
                      <span className="text-[14px] font-bold">{categoryLabel(a.category)} 크리에이터 신청</span>
                      <Badge tone={STATUS_LABEL[a.status].tone}>{STATUS_LABEL[a.status].label}</Badge>
                    </div>
                    <p className="mt-1 text-[12px] text-ink-sub">
                      신청 {formatDotDate(a.submittedAt)}
                      {a.reviewedAt && ` · 결과 ${formatDotDate(a.reviewedAt)}`}
                    </p>
                    {a.rejectReason && <p className="mt-1.5 text-[12px] text-danger">{a.rejectReason}</p>}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? "rounded-card bg-primary/15 px-4 py-3" : "rounded-card bg-fill px-4 py-3"}>
      <p className="text-[12px] text-ink-sub">{label}</p>
      <p className="mt-1 text-[18px] font-bold tabular-nums">{value}</p>
    </div>
  );
}

function MenuRow({ href, icon, label, desc, warn }: { href: string; icon: ReactNode; label: string; desc: string; warn?: boolean }) {
  return (
    <li>
      <Link href={href} className="flex items-center gap-3 px-4 py-3.5">
        <span className="text-primary-deep">{icon}</span>
        <span className="min-w-0 flex-1">
          <span className="block text-[14px] font-bold">{label}</span>
          <span className={warn ? "block text-[12px] text-warning" : "block text-[12px] text-ink-sub"}>{desc}</span>
        </span>
        <ChevronRight size={18} className="text-ink-faint" />
      </Link>
    </li>
  );
}
