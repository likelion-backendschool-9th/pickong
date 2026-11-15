"use client";

import { Lock, Sparkles } from "lucide-react";
import { useMemo, useState } from "react";
import { categoryLabel, formatHours, openStateAt, useSavedPlaces, type Place, type PlaceCategory } from "@/entities/place";
import type { PlanItem } from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { weekday } from "@/shared/lib/date";
import { estimateTravelMinutes } from "@/shared/lib/geo";
import { toMinutes } from "@/shared/lib/time";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { CheckMark, Chip, SelectRow, Stepper } from "@/shared/ui/controls";
import { EmptyState, Notice, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { PlanBasics } from "./SetupStep";

export interface Candidate {
  placeId: string;
  stay: number;
}

/** 방문 후보 선택: 내 지도에 저장한 장소 중에서 고릅니다. */
export function CandidatesStep({
  basics,
  reservations,
  selected,
  onChange,
  onBack,
  onSubmit,
  submitting,
  error,
}: {
  basics: PlanBasics;
  reservations: PlanItem[];
  selected: Candidate[];
  onChange: (c: Candidate[]) => void;
  onBack: () => void;
  onSubmit: (places: Place[]) => void;
  submitting: boolean;
  error: unknown;
}) {
  const { data: saved, isPending } = useSavedPlaces();
  const [region, setRegion] = useState<string>("ALL");
  const [category, setCategory] = useState<PlaceCategory | "ALL">("ALL");
  const [recommendOpen, setRecommendOpen] = useState(false);
  const reservedIds = new Set(reservations.map((r) => r.place.id));

  const places = useMemo(() => (saved ?? []).map((s) => s.place).filter((p) => !reservedIds.has(p.id)), [saved, reservations]); // eslint-disable-line react-hooks/exhaustive-deps
  const regions = [...new Set(places.map((p) => p.region))];
  const categories = [...new Set(places.map((p) => p.category))];
  const visible = places.filter((p) => (region === "ALL" || p.region === region) && (category === "ALL" || p.category === category));

  const isSelected = (id: string) => selected.some((c) => c.placeId === id);
  const toggle = (id: string) =>
    onChange(isSelected(id) ? selected.filter((c) => c.placeId !== id) : [...selected, { placeId: id, stay: 60 }]);
  const setStay = (id: string, stay: number) => onChange(selected.map((c) => (c.placeId === id ? { ...c, stay } : c)));

  return (
    <Screen>
      <TopBar title="방문 후보 선택" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <p className="text-[12px] font-bold text-primary-deep">3 / 3 · 방문 후보</p>
        <h2 className="mt-2 text-[20px] font-bold">어디를 들러 볼까요?</h2>
        <p className="mt-1 text-[13px] text-ink-sub">내 지도에 저장한 장소에서 골라요. 순서는 다음 화면에서 조정할 수 있어요.</p>

        {reservations.length > 0 && (
          <div className="mt-4 rounded-card bg-soft px-4 py-3">
            <p className="text-[12px] font-bold text-ink-sub">고정 예약</p>
            {reservations.map((r) => (
              <p key={r.id} className="mt-1 flex items-center gap-1.5 text-[13px]">
                <Lock size={12} className="text-primary-deep" />
                <span className="font-bold tabular-nums">
                  {r.reservation!.startTime}–{r.reservation!.endTime}
                </span>
                {r.place.name}
              </p>
            ))}
          </div>
        )}

        <div className="scrollbar-none -mx-5 mt-4 flex gap-1.5 overflow-x-auto px-5">
          <Chip selected={region === "ALL"} onClick={() => setRegion("ALL")}>전체 지역</Chip>
          {regions.map((r) => (
            <Chip key={r} selected={region === r} onClick={() => setRegion(r)}>{r}</Chip>
          ))}
        </div>
        <div className="scrollbar-none -mx-5 mt-2 flex gap-1.5 overflow-x-auto px-5">
          <Chip selected={category === "ALL"} onClick={() => setCategory("ALL")}>전체</Chip>
          {categories.map((c) => (
            <Chip key={c} selected={category === c} onClick={() => setCategory(c)}>{categoryLabel(c)}</Chip>
          ))}
        </div>

        <Button variant="outline" size="md" block className="mt-4" icon={<Sparkles size={16} className="text-primary-deep" />} onClick={() => setRecommendOpen(true)}>
          조건에 맞는 장소 추천 받기
        </Button>

        {isPending ? (
          <Skeleton className="mt-4 h-40" />
        ) : places.length === 0 ? (
          <EmptyState title="저장한 장소가 없어요" description="먼저 내 지도에 장소를 저장해 주세요.">
            <ButtonLink href={routes.feed()} variant="secondary" block>피드에서 장소 찾기</ButtonLink>
          </EmptyState>
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {visible.map((p) => {
              const sel = selected.find((c) => c.placeId === p.id);
              const state = openStateAt(p.businessHours, weekday(basics.date), toMinutes(basics.startTime));
              return (
                <li key={p.id} className="py-1">
                  <div
                    role="checkbox"
                    aria-checked={!!sel}
                    tabIndex={0}
                    onClick={() => toggle(p.id)}
                    onKeyDown={(e) => (e.key === " " || e.key === "Enter") && (e.preventDefault(), toggle(p.id))}
                    className="flex cursor-pointer items-center gap-3 py-2"
                  >
                    <CheckMark checked={!!sel} round />
                    <div className="min-w-0 flex-1">
                      <p className="text-[14px] font-bold">{p.name}</p>
                      <p className="text-[12px] text-ink-sub">
                        {p.region} · {categoryLabel(p.category)} · {formatHours(p.businessHours)}
                        {state.type === "CLOSED_DAY" && <span className="text-warning"> · 이날 휴무</span>}
                      </p>
                    </div>
                  </div>
                  {sel && (
                    <div className="mb-2 ml-9 flex items-center gap-3">
                      <span className="text-[12px] text-ink-sub">예상 체류</span>
                      <Stepper label={`${p.name} 체류 시간`} value={sel.stay} onChange={(v) => setStay(p.id, v)} step={10} min={10} max={300} />
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        {!!error && <Notice tone="danger" className="mt-3">{errorMessage(error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={submitting} disabled={selected.length === 0 && reservations.length === 0} onClick={() => onSubmit(places)}>
          일정 만들기 · {selected.length + reservations.length}곳
        </Button>
      </BottomCta>

      <BottomSheet open={recommendOpen} onClose={() => setRecommendOpen(false)} title="조건에 맞는 추천" description={`${basics.region} · 이날 영업 · 출발지에서 가까운 순`}>
        {recommendOpen && (
          <RecommendList
            basics={basics}
            places={places}
            already={selected.map((c) => c.placeId)}
            onConfirm={(ids) => {
              onChange([...selected, ...ids.map((placeId) => ({ placeId, stay: 60 }))]);
              setRecommendOpen(false);
            }}
          />
        )}
      </BottomSheet>
    </Screen>
  );
}

/** 추천은 자동 확정하지 않고, 사용자가 확인하고 고른 것만 후보에 넣습니다. */
function RecommendList({
  basics,
  places,
  already,
  onConfirm,
}: {
  basics: PlanBasics;
  places: Place[];
  already: string[];
  onConfirm: (ids: string[]) => void;
}) {
  const [picked, setPicked] = useState<string[]>([]);
  const day = weekday(basics.date);
  const recs = places
    .filter((p) => !already.includes(p.id))
    .filter((p) => !basics.region || p.region.includes(basics.region.replace("동", "")) || basics.region.includes(p.region))
    .filter((p) => openStateAt(p.businessHours, day, 14 * 60).type !== "CLOSED_DAY")
    .map((p) => ({ p, travel: basics.startPlace ? estimateTravelMinutes(basics.startPlace.location, p.location) : 0 }))
    .sort((a, b) => a.travel - b.travel)
    .slice(0, 6);

  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      {recs.length === 0 ? (
        <Notice>조건에 맞는 저장 장소가 없어요. 지역 조건을 바꾸거나 직접 골라 주세요.</Notice>
      ) : (
        <div className="-mx-3">
          {recs.map(({ p, travel }) => (
            <SelectRow
              key={p.id}
              checked={picked.includes(p.id)}
              onToggle={() => setPicked((s) => (s.includes(p.id) ? s.filter((x) => x !== p.id) : [...s, p.id]))}
              title={p.name}
              description={`${categoryLabel(p.category)} · 영업 ${formatHours(p.businessHours)} · 출발지에서 약 ${travel}분`}
              highlight
            />
          ))}
        </div>
      )}
      <Button block className="mt-4" disabled={picked.length === 0} onClick={() => onConfirm(picked)}>
        선택한 {picked.length}곳 후보에 추가
      </Button>
    </div>
  );
}
