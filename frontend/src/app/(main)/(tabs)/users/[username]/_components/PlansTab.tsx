"use client";

import { CalendarPlus, ChevronRight, Clock3, Pencil, Send, Share2, Trash2 } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import {
  PLAN_STATUS_LABEL,
  planShareText,
  useDeletePlan,
  usePlans,
  useSetPlanStatus,
  type Plan,
} from "@/entities/plan";
import { SendDmSheet } from "@/features/send-dm";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatShortDate } from "@/shared/lib/date";
import { shareOrCopy } from "@/shared/lib/share";
import { ActionMenu } from "@/shared/ui/ActionMenu";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";

/**
 * 내 프로필 일정 탭: 위쪽 탭으로 ‘예정’(계획 중 · 계획 완료)과 ‘지난 일정’(방문 기록)을 나눠 봅니다.
 * 고른 탭은 주소(?tab=plans&plans=past)에 남아 일정을 보고 돌아와도 유지됩니다.
 * 일정은 본인만 볼 수 있어서 내 프로필에만 있습니다.
 */
export function PlansTab() {
  const { data: plans, isPending, isError, error, refetch } = usePlans();
  const [deleting, setDeleting] = useState<Plan | null>(null);
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const view = params.get("plans") === "past" ? "past" : "upcoming";
  const setView = (v: "upcoming" | "past") =>
    router.replace(`${pathname}?tab=plans${v === "past" ? "&plans=past" : ""}`, { scroll: false });
  const upcoming = plans?.filter((p) => p.status !== "DONE") ?? [];
  const past = plans?.filter((p) => p.status === "DONE").reverse() ?? [];

  return (
    <div className="px-5 pt-3">
      <div className="flex gap-2" role="tablist" aria-label="일정 구분">
        <Chip role="tab" aria-pressed={undefined} aria-selected={view === "upcoming"} selected={view === "upcoming"} onClick={() => setView("upcoming")}>
          예정 {plans ? upcoming.length : ""}
        </Chip>
        <Chip role="tab" aria-pressed={undefined} aria-selected={view === "past"} selected={view === "past"} onClick={() => setView("past")}>
          지난 일정 {plans ? past.length : ""}
        </Chip>
      </div>
      {isPending ? (
        <Skeleton className="mt-4 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : view === "upcoming" ? (
        <>
          <p className="mt-3 text-[13px] text-ink-sub">갈 곳의 순서만 정하고, 예약이 있는 곳만 시간을 적어요.</p>
          {upcoming.length === 0 ? (
            <EmptyState title="예정된 일정이 없어요" description="저장한 장소로 하루를 계획해 보세요." />
          ) : (
            <div className="mt-4 space-y-3">
              {upcoming.map((p) => (
                <PlanCard key={p.id} plan={p} onDelete={() => setDeleting(p)} />
              ))}
            </div>
          )}
          <ButtonLink href={routes.newPlan()} block className="mt-4" icon={<CalendarPlus size={18} />}>
            새 일정 만들기
          </ButtonLink>
        </>
      ) : (
        <>
          <p className="mt-3 text-[13px] text-ink-sub">방문 기록을 마친 일정이에요. 눌러서 다녀온 곳을 다시 볼 수 있어요.</p>
          {past.length === 0 ? (
            <EmptyState title="아직 지난 일정이 없어요" description="일정을 다녀온 뒤 방문 기록을 남기면 여기에 쌓여요." />
          ) : (
            <ul className="mt-2 divide-y divide-line">
              {past.map((p) => (
                <li key={p.id}>
                  <Link href={routes.plan(p.id)} className="flex items-center gap-3 py-3.5">
                    <div className="min-w-0 flex-1">
                      <p className="text-[14px] font-bold">
                        {formatShortDate(p.date)} {p.title}
                      </p>
                      <p className="text-[12px] text-ink-sub">
                        {p.region && `${p.region} · `}
                        {p.items.filter((i) => i.visited).length}곳 방문
                      </p>
                    </div>
                    <ChevronRight size={18} className="text-ink-faint" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      <DeleteSheet plan={deleting} onClose={() => setDeleting(null)} />
    </div>
  );
}

/** 예정 일정 카드: 오른쪽 위에 DM 보내기 · 공유 버튼과 ⋯ 메뉴(수정하기 · 삭제하기) */
function PlanCard({ plan, onDelete }: { plan: Plan; onDelete: () => void }) {
  const router = useRouter();
  const toast = useToast();
  const setStatus = useSetPlanStatus(plan.id);
  const [dmOpen, setDmOpen] = useState(false);
  const fixed = plan.items.filter((i) => i.fixedTime).sort((a, b) => a.fixedTime!.localeCompare(b.fixedTime!));

  const share = async () => {
    const result = await shareOrCopy({ title: plan.title, text: planShareText(plan) });
    if (result === "copied") toast({ message: "일정을 복사했어요", detail: "메신저에 붙여 넣어 공유해 보세요" });
  };

  // 수정하기: 계획 완료 상태면 ‘계획 중’으로 되돌린 뒤 편집 화면으로
  const edit = () => {
    if (plan.status !== "UPCOMING") return router.push(routes.plan(plan.id));
    setStatus.mutate("PLANNING", {
      onSuccess: () => router.push(routes.plan(plan.id)),
      onError: (e) => toast({ message: errorMessage(e) }),
    });
  };

  return (
    <div className="relative rounded-card bg-soft">
      <Link href={routes.plan(plan.id)} className="block rounded-card p-4 pr-32 active:bg-selected">
        <span
          className={cn(
            "inline-block rounded-md px-1.5 py-0.5 text-[11px] font-bold",
            plan.status === "PLANNING" ? "bg-warning-soft text-warning" : "bg-selected text-primary-deep",
          )}
        >
          {PLAN_STATUS_LABEL[plan.status]}
        </span>
        <p className="mt-1.5 text-[16px] font-bold">{plan.title}</p>
        <p className="mt-1 text-[12px] text-ink-sub">
          {formatShortDate(plan.date)}
          {plan.region && ` · ${plan.region}`} · 장소 {plan.items.length}곳
        </p>
        {fixed.length > 0 && (
          <ul className="mt-3 space-y-1">
            {fixed.map((it) => (
              <li key={it.id} className="flex items-center gap-1.5 text-[13px]">
                <Clock3 size={12} className="text-primary-deep" />
                <span className="font-bold tabular-nums">{it.fixedTime}</span> {it.place.name}
              </li>
            ))}
          </ul>
        )}
      </Link>
      <div className="absolute top-2 right-2 flex items-center">
        <button
          type="button"
          onClick={() => setDmOpen(true)}
          aria-label={`${plan.title} DM으로 보내기`}
          aria-haspopup="dialog"
          className="inline-flex size-9 items-center justify-center rounded-full text-ink-sub active:bg-fill"
        >
          <Send size={18} />
        </button>
        <button
          type="button"
          onClick={share}
          aria-label={`${plan.title} 공유하기`}
          className="inline-flex size-9 items-center justify-center rounded-full text-ink-sub active:bg-fill"
        >
          <Share2 size={18} />
        </button>
        <ActionMenu
          label={`${plan.title} 메뉴`}
          items={[
            { label: "수정하기", icon: <Pencil size={15} />, onSelect: edit },
            { label: "삭제하기", icon: <Trash2 size={15} />, onSelect: onDelete, danger: true },
          ]}
        />
      </div>
      <SendDmSheet open={dmOpen} onClose={() => setDmOpen(false)} text={planShareText(plan)} title="일정을 DM으로 보내기" />
    </div>
  );
}

function DeleteSheet({ plan, onClose }: { plan: Plan | null; onClose: () => void }) {
  const remove = useDeletePlan();
  const toast = useToast();
  return (
    <BottomSheet open={!!plan} onClose={onClose} title="이 일정을 삭제할까요?" description={plan ? `${plan.title} · 삭제하면 되돌릴 수 없어요.` : undefined}>
      {plan && (
        <div className="grid grid-cols-2 gap-2 pb-[calc(12px+env(safe-area-inset-bottom))]">
          <Button variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="danger"
            loading={remove.isPending}
            onClick={() =>
              remove.mutate(plan.id, {
                onSuccess: () => {
                  onClose();
                  toast({ message: "일정을 삭제했어요" });
                },
                onError: (e) => toast({ message: errorMessage(e) }),
              })
            }
          >
            삭제
          </Button>
        </div>
      )}
    </BottomSheet>
  );
}
