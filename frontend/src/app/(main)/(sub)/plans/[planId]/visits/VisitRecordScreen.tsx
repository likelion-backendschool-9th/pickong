"use client";

import { CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { categoryLabel } from "@/entities/place";
import { usePlan, useRecordVisits, type Plan } from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { SelectRow } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";

export function VisitRecordScreen({ planId }: { planId: string }) {
  const { data: plan, isPending, isError, error } = usePlan(planId);
  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  return <VisitForm plan={plan} />;
}

/** 방문 기록: 방문한 장소와 미방문 장소를 나누고, 게시물 공유로 이어집니다. */
function VisitForm({ plan }: { plan: Plan }) {
  const [visited, setVisited] = useState<string[]>(plan.items.filter((i) => i.visited).map((i) => i.id));
  const [done, setDone] = useState(false);
  const record = useRecordVisits(plan.id);
  const toggle = (id: string) => setVisited((v) => (v.includes(id) ? v.filter((x) => x !== id) : [...v, id]));
  const notVisited = plan.items.filter((i) => !visited.includes(i.id));

  if (done) {
    return (
      <Screen>
        <TopBar title="방문 기록" backHref={routes.plan(plan.id)} />
        <div className="flex flex-1 flex-col items-center px-6 pt-16 text-center">
          <CheckCircle2 size={44} className="text-primary-deep" />
          <h2 className="mt-4 text-[20px] font-bold">방문 기록을 남겼어요</h2>
          <p className="mt-2 text-[13px] text-ink-sub">
            {visited.length}곳은 내 지도에서 ‘방문 완료’로 바뀌었어요.
            {notVisited.length > 0 && ` 가지 못한 ${notVisited.length}곳은 다음 일정 후보로 남아요.`}
          </p>
        </div>
        <BottomCta className="space-y-2">
          <ButtonLink href={`${routes.newPost()}?fromPlan=${plan.id}`} block replace>
            다녀온 장소를 게시물로 공유하기
          </ButtonLink>
          <ButtonLink href={routes.plan(plan.id)} variant="secondary" block replace>
            일정으로 돌아가기
          </ButtonLink>
        </BottomCta>
      </Screen>
    );
  }

  return (
    <Screen>
      <TopBar title="방문 기록" backHref={routes.plan(plan.id)} />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[20px] font-bold">어떤 곳에 다녀왔나요?</h2>
        <p className="mt-1 text-[13px] text-ink-sub">
          {formatShortDate(plan.date)} · {plan.title}
        </p>
        <div className="-mx-3 mt-4">
          {plan.items.map((it) => (
            <SelectRow
              key={it.id}
              checked={visited.includes(it.id)}
              onToggle={() => toggle(it.id)}
              title={it.place.name}
              description={`${categoryLabel(it.place.category)}${it.kind === "RESERVATION" ? ` · 예약 ${it.reservation!.startTime}` : ""}`}
              highlight
            />
          ))}
        </div>
        <p className="mt-3 text-[12px] text-ink-faint">방문하지 않은 곳은 내 지도에 ‘가고 싶은 곳’으로 남아요.</p>
        {record.isError && <Notice tone="danger" className="mt-3">{errorMessage(record.error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={record.isPending} onClick={() => record.mutate(visited, { onSuccess: () => setDone(true) })}>
          방문 {visited.length}곳 기록하기
        </Button>
      </BottomCta>
    </Screen>
  );
}
