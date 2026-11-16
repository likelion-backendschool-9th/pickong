"use client";

import { CalendarPlus, ChevronRight, Lock } from "lucide-react";
import Link from "next/link";
import { usePlans, type Plan } from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { EmptyState, ErrorState, SectionTitle, Skeleton } from "@/shared/ui/display";

/**
 * 내 프로필 일정 탭: 곧 떠나는 하루 + 지난 일정(방문 기록).
 * 일정은 본인만 볼 수 있어서 내 프로필에만 있습니다.
 */
export function PlansTab() {
  const { data: plans, isPending, isError, error, refetch } = usePlans();
  const upcoming = plans?.filter((p) => p.status === "UPCOMING") ?? [];
  const past = plans?.filter((p) => p.status === "DONE").reverse() ?? [];

  return (
    <div className="px-5 pt-4">
      {isPending ? (
        <Skeleton className="mt-2 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <>
          <h2 className="text-[20px] font-bold">곧 떠나는 하루</h2>
          <p className="mt-1 text-[13px] text-ink-sub">확정 예약은 고정하고, 나머지 일정만 조정해요.</p>
          {upcoming.length === 0 ? (
            <EmptyState title="예정된 일정이 없어요" description="저장한 장소로 하루 동선을 만들어 보세요." />
          ) : (
            <div className="mt-4 space-y-3">
              {upcoming.map((p) => (
                <PlanCard key={p.id} plan={p} />
              ))}
            </div>
          )}
          <ButtonLink href={routes.newPlan()} block className="mt-4" icon={<CalendarPlus size={18} />}>
            새 일정 만들기
          </ButtonLink>

          {past.length > 0 && (
            <>
              <SectionTitle className="mt-8 mb-1">지난 일정 · 방문 기록</SectionTitle>
              <ul className="divide-y divide-line">
                {past.map((p) => (
                  <li key={p.id}>
                    <Link href={routes.plan(p.id)} className="flex items-center gap-3 py-3.5">
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-bold">
                          {formatShortDate(p.date)} {p.title}
                        </p>
                        <p className="text-[12px] text-ink-sub">
                          {p.region} · {p.items.filter((i) => i.visited).length}곳 방문
                        </p>
                      </div>
                      <ChevronRight size={18} className="text-ink-faint" />
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </div>
  );
}

function PlanCard({ plan }: { plan: Plan }) {
  const reservations = plan.items.filter((i) => i.kind === "RESERVATION");
  return (
    <Link href={routes.plan(plan.id)} className="block rounded-card bg-soft p-4 active:bg-selected">
      <p className="text-[16px] font-bold">{plan.title}</p>
      <p className="mt-1 text-[12px] text-ink-sub">
        {formatShortDate(plan.date)} · {plan.region} · {plan.startTime} 출발 · 장소 {plan.items.length}곳
      </p>
      {reservations.length > 0 && (
        <ul className="mt-3 space-y-1">
          {reservations.map((r) => (
            <li key={r.id} className="flex items-center gap-1.5 text-[13px]">
              <Lock size={12} className="text-primary-deep" />
              <span className="font-bold tabular-nums">{r.reservation!.startTime}</span> {r.place.name}
            </li>
          ))}
        </ul>
      )}
    </Link>
  );
}
