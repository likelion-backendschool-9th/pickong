"use client";

import { CalendarCheck, List, Map as MapIcon, Pencil, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { fixedTimeOrderIssue, PLAN_STATUS_LABEL, usePlan, useSetPlanStatus, type Plan } from "@/entities/plan";
import { useMe } from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatShortDate } from "@/shared/lib/date";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { EmptyState, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { usePlanEditor } from "../_hooks/usePlanEditor";
import { PlanList } from "./PlanList";
import { PlanMap } from "./PlanMap";
import { AddPlaceSheet, ItemSheet } from "./sheets";

export function PlanEditor({ planId }: { planId: string }) {
  const { data: plan, isPending, isError, error } = usePlan(planId);
  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  return <Editor plan={plan} />;
}

/**
 * 일정 화면
 * - 계획 중: 장소 순서를 끌어서 정하고, 장소를 눌러 고정 시간(예약 시간)을 적은 뒤 ‘계획 완료’
 * - 계획 완료: 보기 전용. 방문 기록하기 · 계획 수정
 * - 다녀옴: 방문 기록 보기 · 게시물로 공유
 */
function Editor({ plan }: { plan: Plan }) {
  const editor = usePlanEditor(plan);
  const { items } = editor;
  const setStatus = useSetPlanStatus(plan.id);
  const [view, setView] = useState<"list" | "map">("list");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const toast = useToast();
  const router = useRouter();
  const { data: me } = useMe();
  const planning = plan.status === "PLANNING";
  const selected = items.find((i) => i.id === selectedId) ?? null;
  const orderIssue = planning ? fixedTimeOrderIssue(items) : null;

  const changeStatus = (status: "PLANNING" | "UPCOMING") =>
    setStatus.mutate(status, {
      onSuccess: () => {
        toast({ message: status === "UPCOMING" ? "계획을 완료했어요" : "다시 수정할 수 있어요" });
        // 계획 완료 후에는 전체 일정 목록(내 프로필 일정 탭)으로 돌아갑니다.
        if (status === "UPCOMING") router.push(me ? routes.myPlans(me.username) : routes.plans());
      },
      onError: (e) => toast({ message: errorMessage(e) }),
    });

  return (
    <div className="flex min-h-[calc(100dvh-76px-env(safe-area-inset-bottom))] flex-col">
      <TopBar
        title={plan.title}
        backHref={routes.plans()}
        right={
          <Chip onClick={() => setView(view === "map" ? "list" : "map")} icon={view === "map" ? <List size={14} /> : <MapIcon size={14} />} className="mr-2">
            {view === "map" ? "목록" : "지도"}
          </Chip>
        }
      />
      <div className="flex-1 px-5 pb-6">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "rounded-md px-2 py-0.5 text-[12px] font-bold",
              planning ? "bg-warning-soft text-warning" : plan.status === "UPCOMING" ? "bg-selected text-primary-deep" : "bg-fill text-ink-sub",
            )}
          >
            {PLAN_STATUS_LABEL[plan.status]}
          </span>
          <p className="min-w-0 flex-1 truncate text-[13px] text-ink-sub">
            {formatShortDate(plan.date)}
            {plan.region && ` · ${plan.region}`}
            {plan.memo && ` · ${plan.memo}`}
          </p>
        </div>
        {planning && (
          <p className="mt-1 text-[12px] text-ink-faint" aria-live="polite">
            {editor.saving ? "저장 중…" : "끌어서 순서를 바꾸고, 예약이 있는 곳은 눌러서 시간을 적어요"}
          </p>
        )}

        {orderIssue && (
          <Notice tone="warning" className="mt-3">
            {orderIssue.earlier.place.name}({orderIssue.earlier.fixedTime})이 {orderIssue.later.place.name}({orderIssue.later.fixedTime})보다 앞에 있어요.
          </Notice>
        )}

        <div className="mt-4">
          {items.length === 0 ? (
            <EmptyState title="아직 넣은 장소가 없어요" description="아래에서 장소를 추가해 보세요." />
          ) : view === "list" ? (
            <PlanList items={items} editable={planning} onMove={editor.move} onSelect={setSelectedId} />
          ) : (
            <PlanMap items={items} />
          )}
        </div>

        {planning && (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="mt-4 flex h-12 w-full items-center justify-center gap-1.5 rounded-card border border-dashed border-line text-[14px] font-bold text-primary-deep"
          >
            <Plus size={17} /> 장소 추가
          </button>
        )}

        {plan.status === "DONE" && (
          <div className="mt-6 flex flex-col gap-2">
            <ButtonLink href={routes.planVisits(plan.id)} variant="secondary" block icon={<CalendarCheck size={18} />}>
              방문 기록 보기
            </ButtonLink>
            <Link href={`${routes.newPost()}?fromPlan=${plan.id}`} className="py-2 text-center text-[13px] font-bold text-primary-deep">
              다녀온 장소를 게시물로 공유하기 →
            </Link>
          </div>
        )}
      </div>

      {planning && (
        <ActionBar>
          <Button block loading={setStatus.isPending} disabled={items.length === 0 || editor.saving} onClick={() => changeStatus("UPCOMING")}>
            계획 완료
          </Button>
        </ActionBar>
      )}
      {plan.status === "UPCOMING" && (
        <ActionBar className="grid grid-cols-[auto_1fr] gap-2">
          <Button variant="secondary" icon={<Pencil size={16} />} loading={setStatus.isPending} onClick={() => changeStatus("PLANNING")}>
            계획 수정
          </Button>
          <ButtonLink href={routes.planVisits(plan.id)} block icon={<CalendarCheck size={18} />}>
            방문 기록하기
          </ButtonLink>
        </ActionBar>
      )}

      <ItemSheet
        item={selected}
        onClose={() => setSelectedId(null)}
        onSave={(time) => {
          if (selected) editor.setFixedTime(selected.id, time);
          setSelectedId(null);
        }}
        onRemove={() => {
          if (!selected) return;
          editor.remove(selected.id);
          setSelectedId(null);
          toast({ message: `${selected.place.name}을(를) 일정에서 뺐어요` });
        }}
      />
      <AddPlaceSheet
        open={adding}
        items={items}
        onClose={() => setAdding(false)}
        onAdd={(place) => {
          editor.add({ id: `tmp-${Date.now()}`, place, fixedTime: null, visited: false });
          setAdding(false);
          toast({ message: `${place.name}을(를) 맨 뒤에 넣었어요` });
        }}
      />
    </div>
  );
}

/** 하단 고정 버튼 영역: 이 화면은 하단바가 있어서 하단바 바로 위에 붙습니다. */
function ActionBar({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("sticky bottom-[calc(60px+env(safe-area-inset-bottom))] z-20 bg-bg/95 px-5 py-3 backdrop-blur", className)}>{children}</div>
  );
}
