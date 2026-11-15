"use client";

import { AlertTriangle, CalendarCheck, List, Map as MapIcon, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { usePlan, type Plan } from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { usePlanEditor } from "../_hooks/usePlanEditor";
import { PlanMap } from "./PlanMap";
import { PlanTimeline } from "./PlanTimeline";
import { AddPlaceSheet, ConflictSheet, GapSheet, StaySheet, SuggestSheet } from "./sheets";

export function PlanEditor({ planId }: { planId: string }) {
  const { data: plan, isPending, isError, error } = usePlan(planId);
  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  return <Editor plan={plan} />;
}

type Sheet =
  | { type: "conflict"; itemId: string }
  | { type: "suggest" }
  | { type: "stay"; itemId: string }
  | { type: "gap"; index: number }
  | { type: "add" }
  | null;

function Editor({ plan }: { plan: Plan }) {
  const editor = usePlanEditor(plan);
  const { items, timeline } = editor;
  const [view, setView] = useState<"timeline" | "map">("timeline");
  const [sheet, setSheet] = useState<Sheet>(null);
  const toast = useToast();
  const readOnly = plan.status === "DONE";
  const close = () => setSheet(null);

  const conflictTarget = (() => {
    if (sheet?.type !== "conflict") return null;
    const idx = items.findIndex((i) => i.id === sheet.itemId);
    const warning = timeline.entries[idx]?.warnings[0];
    return idx >= 0 && warning ? { item: items[idx], warning } : null;
  })();
  const firstWarningIdx = timeline.entries.findIndex((e) => e.warnings.length > 0);
  const stayIdx = sheet?.type === "stay" ? items.findIndex((i) => i.id === sheet.itemId) : -1;

  const exclude = (id: string) => {
    const name = items.find((i) => i.id === id)?.place.name;
    editor.remove(id);
    close();
    toast({ message: `${name}을(를) 일정에서 뺐어요`, detail: "지도 핀과 경로도 함께 바뀌었어요" });
  };

  return (
    <div>
      <TopBar
        title={plan.title}
        backHref={routes.plans()}
        right={
          <Chip onClick={() => setView(view === "map" ? "timeline" : "map")} icon={view === "map" ? <List size={14} /> : <MapIcon size={14} />} className="mr-2">
            {view === "map" ? "타임라인" : "지도"}
          </Chip>
        }
      />
      <div className="px-5 pb-8">
        <p className="text-[13px] text-ink-sub">
          {formatShortDate(plan.date)} · {plan.region}
          {plan.memo && ` · ${plan.memo}`}
        </p>
        <p className="mt-0.5 text-[12px] text-ink-faint" aria-live="polite">
          {editor.calculating ? "이동 시간 계산 중 · 새 순서는 그대로 유지돼요" : editor.saving ? "저장 중…" : readOnly ? "지난 일정 · 방문 기록 완료" : "모든 변경은 자동 저장돼요"}
        </p>

        {timeline.warningCount > 0 && !readOnly && (
          <button
            type="button"
            onClick={() => setSheet({ type: "conflict", itemId: items[firstWarningIdx].id })}
            className="mt-3 flex w-full items-center gap-2 rounded-card bg-warning-soft px-4 py-3 text-left"
          >
            <AlertTriangle size={18} className="shrink-0 text-warning" />
            <span className="min-w-0 flex-1 text-[13px]">
              <span className="font-bold">일정 점검이 필요해요 · {timeline.warningCount}건</span>
              <span className="block text-ink-sub">순서는 바꾸지 않았어요. 해결 방법을 골라 주세요.</span>
            </span>
          </button>
        )}

        <div className="mt-4">
          {view === "timeline" ? (
            <PlanTimeline
              plan={plan}
              items={items}
              timeline={timeline}
              readOnly={readOnly}
              onMove={editor.move}
              onWarning={(itemId) => setSheet({ type: "conflict", itemId })}
              onStay={(itemId) => setSheet({ type: "stay", itemId })}
              onGap={(index) => setSheet({ type: "gap", index })}
            />
          ) : (
            <PlanMap plan={plan} items={items} timeline={timeline} readOnly={readOnly} onRemove={exclude} />
          )}
        </div>

        {!readOnly && (
          <button
            type="button"
            onClick={() => setSheet({ type: "add" })}
            className="mt-4 flex h-12 w-full items-center justify-center gap-1.5 rounded-card border border-dashed border-line text-[14px] font-bold text-primary-deep"
          >
            <Plus size={17} /> 내 지도에서 장소 추가
          </button>
        )}

        <div className="mt-6 flex flex-col gap-2">
          {!readOnly && (
            <button
              type="button"
              onClick={() => setSheet({ type: "suggest" })}
              className="h-11 text-[13px] font-bold text-ink-sub"
            >
              동선 순서 추천 받기
            </button>
          )}
          <ButtonLink href={routes.planVisits(plan.id)} variant={readOnly ? "secondary" : "primary"} block icon={<CalendarCheck size={18} />}>
            {readOnly ? "방문 기록 보기" : "방문 기록하기"}
          </ButtonLink>
          {readOnly && (
            <Link href={`${routes.newPost()}?fromPlan=${plan.id}`} className="py-2 text-center text-[13px] font-bold text-primary-deep">
              다녀온 장소를 게시물로 공유하기 →
            </Link>
          )}
        </div>
      </div>

      <ConflictSheet
        target={conflictTarget}
        items={items}
        onClose={close}
        onSuggest={() => setSheet({ type: "suggest" })}
        onExclude={exclude}
        onStay={(itemId) => setSheet({ type: "stay", itemId })}
      />
      <SuggestSheet
        open={sheet?.type === "suggest"}
        plan={plan}
        items={items}
        current={timeline}
        onClose={close}
        onApply={(next) => {
          editor.replaceAll(next);
          close();
          toast({ message: "추천 순서를 적용했어요" });
        }}
      />
      <StaySheet
        item={stayIdx >= 0 ? items[stayIdx] : null}
        startAt={stayIdx >= 0 ? timeline.entries[stayIdx].startAt : 0}
        onClose={close}
        onApply={(m) => {
          if (sheet?.type === "stay") editor.setStay(sheet.itemId, m);
          close();
        }}
      />
      <GapSheet
        gap={
          sheet?.type === "gap"
            ? {
                index: sheet.index,
                startAt: sheet.index === 0 ? timeline.startAt : timeline.entries[sheet.index - 1].departAt,
                endAt: timeline.entries[sheet.index].startAt,
              }
            : null
        }
        plan={plan}
        items={items}
        onClose={close}
        onAdd={(index, item) => {
          editor.insert(index, item);
          close();
          toast({ message: `${item.place.name}을(를) 빈 시간에 넣었어요` });
        }}
      />
      <AddPlaceSheet
        open={sheet?.type === "add"}
        items={items}
        onClose={close}
        onAdd={(item) => {
          editor.insert(items.length, item);
          close();
        }}
      />
    </div>
  );
}
