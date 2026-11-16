"use client";

import { CheckCircle2, Plus, X } from "lucide-react";
import { useState } from "react";
import { categoryLabel, useSavedPlaces, type Place } from "@/entities/place";
import { usePlan, useRecordVisits, type Plan } from "@/entities/plan";
import { PlaceSearchPanel } from "@/features/place-search";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
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

/**
 * 방문 기록: 다녀온 곳을 체크하고(전체 선택 가능), 일정에 없었지만 들른 곳도 추가합니다.
 * 기록하면 게시물 공유로 이어집니다.
 */
function VisitForm({ plan }: { plan: Plan }) {
  const [visited, setVisited] = useState<string[]>(plan.items.filter((i) => i.visited).map((i) => i.id));
  const [extras, setExtras] = useState<Place[]>([]);
  const [adding, setAdding] = useState(false);
  const [done, setDone] = useState(false);
  const record = useRecordVisits(plan.id);
  const { data: saved = [] } = useSavedPlaces();
  const toggle = (id: string) => setVisited((v) => (v.includes(id) ? v.filter((x) => x !== id) : [...v, id]));
  const allChecked = plan.items.length > 0 && visited.length === plan.items.length;
  const toggleAll = () => setVisited(allChecked ? [] : plan.items.map((i) => i.id));
  const notVisited = plan.items.filter((i) => !visited.includes(i.id));
  const total = visited.length + extras.length;
  const taken = new Set([...plan.items.map((i) => i.place.id), ...extras.map((p) => p.id)]);
  const recent = saved
    .filter((s) => !taken.has(s.place.id))
    .map((s) => ({ place: s.place, note: `${s.place.region} · ${categoryLabel(s.place.category)}` }));

  if (done) {
    return (
      <Screen>
        <TopBar title="방문 기록" backHref={routes.plan(plan.id)} />
        <div className="flex flex-1 flex-col items-center px-6 pt-16 text-center">
          <CheckCircle2 size={44} className="text-primary-deep" />
          <h2 className="mt-4 text-[20px] font-bold">방문 기록을 남겼어요</h2>
          <p className="mt-2 text-[13px] text-ink-sub">
            {total}곳은 내 지도에서 ‘방문 완료’로 바뀌었어요.
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
        <div className="mt-4 flex items-center justify-between">
          <span className="text-[13px] font-bold">일정에 넣은 곳 {plan.items.length}</span>
          <button type="button" onClick={toggleAll} aria-pressed={allChecked} className="h-9 rounded-full px-3 text-[13px] font-bold text-primary-deep active:bg-soft">
            {allChecked ? "전체 해제" : "전체 선택"}
          </button>
        </div>
        <div className="-mx-3 mt-1">
          {plan.items.map((it) => (
            <SelectRow
              key={it.id}
              checked={visited.includes(it.id)}
              onToggle={() => toggle(it.id)}
              title={it.place.name}
              description={`${categoryLabel(it.place.category)}${it.fixedTime ? ` · ${it.fixedTime}` : ""}`}
              highlight
            />
          ))}
        </div>

        <p className="mt-6 text-[13px] font-bold">일정에 없었지만 들른 곳 {extras.length > 0 && extras.length}</p>
        {extras.length > 0 && (
          <ul className="mt-1 divide-y divide-line">
            {extras.map((p) => (
              <li key={p.id} className="flex items-center gap-3 py-2.5">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[14px] font-bold">{p.name}</span>
                  <span className="block text-[12px] text-ink-sub">
                    {p.region} · {categoryLabel(p.category)}
                  </span>
                </span>
                <button
                  type="button"
                  onClick={() => setExtras((list) => list.filter((x) => x.id !== p.id))}
                  aria-label={`${p.name} 빼기`}
                  className="inline-flex size-9 items-center justify-center rounded-full text-ink-faint active:bg-fill"
                >
                  <X size={16} />
                </button>
              </li>
            ))}
          </ul>
        )}
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-2 flex h-12 w-full items-center justify-center gap-1.5 rounded-card border border-dashed border-line text-[14px] font-bold text-primary-deep"
        >
          <Plus size={17} /> 다른 곳 추가
        </button>

        <p className="mt-3 text-[12px] text-ink-faint">
          방문하지 않은 곳은 내 지도에 ‘가고 싶은 곳’으로 남고, 추가한 곳은 일정 끝에 붙어 ‘방문 완료’로 저장돼요.
        </p>
        {record.isError && <Notice tone="danger" className="mt-3">{errorMessage(record.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          loading={record.isPending}
          onClick={() =>
            record.mutate({ visitedItemIds: visited, extraPlaceIds: extras.map((p) => p.id) }, { onSuccess: () => setDone(true) })
          }
        >
          방문 {total}곳 기록하기
        </Button>
      </BottomCta>

      <BottomSheet open={adding} onClose={() => setAdding(false)} title="다른 곳 추가" description="일정에 없었지만 다녀온 곳을 골라요." className="h-[85dvh]">
        {adding && (
          <PlaceSearchPanel
            autoFocus
            recentTitle="내가 저장한 장소"
            recent={recent}
            onSelect={(place) => {
              if (!taken.has(place.id)) setExtras((list) => [...list, place]);
              setAdding(false);
            }}
          />
        )}
      </BottomSheet>
    </Screen>
  );
}
