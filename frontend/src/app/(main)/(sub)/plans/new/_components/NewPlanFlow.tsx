"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useSavedPlaces, type Place } from "@/entities/place";
import { useCreatePlan } from "@/entities/plan";
import { routes } from "@/shared/config/routes";
import { CandidatesStep } from "./CandidatesStep";
import { SetupStep, type PlanBasics } from "./SetupStep";

function nextSaturday(): string {
  const d = new Date();
  d.setDate(d.getDate() + ((6 - d.getDay() + 7) % 7 || 7));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * 일정 만들기: 날짜 · 지역 → 갈 곳 고르기 → (일정 화면에서) 순서 · 고정 시간 정하고 ‘계획 완료’.
 * 체류 시간 · 출발 시간은 정하지 않고, 영화 · 식당 예약처럼 시간이 정해진 곳만 고정 시간을 적습니다.
 */
export function NewPlanFlow() {
  const router = useRouter();
  const params = useSearchParams();
  const [step, setStep] = useState<"setup" | "places">("setup");
  const [basics, setBasics] = useState<PlanBasics>({ title: "", date: nextSaturday(), region: "", memo: "" });
  const [selected, setSelected] = useState<Place[]>([]);
  const create = useCreatePlan();

  // 내 지도에서 고른 장소로 시작한 경우 (?places=a,b)
  const { data: saved } = useSavedPlaces();
  const preselectedDone = useRef(false);
  useEffect(() => {
    const ids = (params.get("places") ?? "").split(",").filter(Boolean);
    if (preselectedDone.current || !saved || ids.length === 0) return;
    preselectedDone.current = true;
    setSelected(ids.flatMap((id) => saved.find((s) => s.place.id === id)?.place ?? []));
  }, [params, saved]);

  const submit = () =>
    create.mutate(
      {
        title: basics.title.trim() || `${basics.region || "서울"}에서 보내는 하루`,
        date: basics.date,
        region: basics.region,
        memo: basics.memo,
        items: selected.map((place, i) => ({ id: `tmp-${i}`, place, fixedTime: null, visited: false })),
      },
      { onSuccess: (plan) => router.replace(routes.plan(plan.id)) },
    );

  if (step === "setup") return <SetupStep value={basics} onChange={setBasics} onNext={() => setStep("places")} />;
  return (
    <CandidatesStep
      selected={selected}
      onChange={setSelected}
      onBack={() => setStep("setup")}
      onSubmit={submit}
      submitting={create.isPending}
      error={create.error}
    />
  );
}
