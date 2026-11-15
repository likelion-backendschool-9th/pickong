"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { usePlaceSearch, type Place } from "@/entities/place";
import { arrangeInitial, useCreatePlan, type PlanItem } from "@/entities/plan";
import { routes } from "@/shared/config/routes";
import { CandidatesStep, type Candidate } from "./CandidatesStep";
import { ReservationsStep } from "./ReservationsStep";
import { SetupStep, type PlanBasics } from "./SetupStep";

function nextSaturday(): string {
  const d = new Date();
  d.setDate(d.getDate() + ((6 - d.getDay() + 7) % 7 || 7));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** 일정 생성: 날짜·지역 설정 → 외부 확정 예약 등록 → 방문 후보 선택 → 하루 타임라인 */
export function NewPlanFlow() {
  const router = useRouter();
  const params = useSearchParams();
  const preselected = (params.get("places") ?? "").split(",").filter(Boolean);
  const [step, setStep] = useState<"setup" | "reservations" | "candidates">("setup");
  const [basics, setBasics] = useState<PlanBasics>({
    title: "",
    date: nextSaturday(),
    region: "성수동",
    startPlace: null,
    startTime: "10:00",
    endTime: "20:00",
    memo: "",
  });
  const [reservations, setReservations] = useState<PlanItem[]>([]);
  const [candidates, setCandidates] = useState<Candidate[]>(preselected.map((placeId) => ({ placeId, stay: 60 })));
  const create = useCreatePlan();

  // 출발 위치 기본값: 성수역 (예시)
  const { data: stations } = usePlaceSearch("성수역");
  const effective: PlanBasics = { ...basics, startPlace: basics.startPlace ?? stations?.[0] ?? null };

  const submit = (places: Place[]) => {
    const startPlace = effective.startPlace!;
    const free: PlanItem[] = candidates.flatMap((c, i) => {
      const place = places.find((p) => p.id === c.placeId);
      return place ? [{ id: `tmp-f${i}`, kind: "FREE" as const, place, stayMinutes: c.stay, visited: false, reservation: null }] : [];
    });
    const items = arrangeInitial({ ...effective, startPlace }, reservations, free);
    create.mutate(
      {
        title: basics.title.trim() || `${basics.region}에서 보내는 하루`,
        date: basics.date,
        region: basics.region,
        startPlaceId: startPlace.id,
        startTime: basics.startTime,
        endTime: basics.endTime,
        memo: basics.memo,
        items,
      },
      { onSuccess: (plan) => router.replace(routes.plan(plan.id)) },
    );
  };

  if (step === "setup") return <SetupStep value={effective} onChange={setBasics} onNext={() => setStep("reservations")} />;
  if (step === "reservations")
    return (
      <ReservationsStep
        date={basics.date}
        reservations={reservations}
        onChange={setReservations}
        onBack={() => setStep("setup")}
        onNext={() => setStep("candidates")}
      />
    );
  return (
    <CandidatesStep
      basics={effective}
      reservations={reservations}
      selected={candidates}
      onChange={setCandidates}
      onBack={() => setStep("reservations")}
      onSubmit={submit}
      submitting={create.isPending}
      error={create.error}
    />
  );
}
