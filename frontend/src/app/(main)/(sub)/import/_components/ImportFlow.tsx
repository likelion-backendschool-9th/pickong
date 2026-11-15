"use client";

import { useState } from "react";
import { useSavePlaces, type Place, type SaveResult } from "@/entities/place";
import { useSavedPlaces } from "@/entities/place";
import { importApi } from "../_api/importApi";
import type { ImportCandidate, ImportJob } from "../_api/mapper";
import { useImportPolling, useStartImport } from "../_api/queries";
import { applyFix, excludeCandidate, initialSelection, saveTargets } from "../_lib/candidateSelection";
import { CollectionStep } from "./CollectionStep";
import { DoneStep } from "./DoneStep";
import { FixLocationStep } from "./FixLocationStep";
import { InputStep } from "./InputStep";
import { ProgressStep } from "./ProgressStep";
import { ReviewStep } from "./ReviewStep";

type Step =
  | { name: "input"; failed?: string; mode: "url" | "text" }
  | { name: "progress" }
  | { name: "review" }
  | { name: "fix"; tempId: string }
  | { name: "collection" }
  | { name: "done"; result: SaveResult; collectionIds: string[] };

/** 단계형 수집: 링크 입력 → 분석 중 → 추출 장소 검토 → 위치 확인·수정 → 컬렉션 선택 → 일괄 저장 */
export function ImportFlow() {
  const [step, setStep] = useState<Step>({ name: "input", mode: "url" });
  const [jobId, setJobId] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<ImportCandidate[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const start = useStartImport();
  const [job, setJob] = useState<ImportJob | null>(null);
  const { data: saved = [] } = useSavedPlaces();
  const save = useSavePlaces();

  useImportPolling(step.name === "progress" ? jobId : null, {
    onProgress: setJob,
    onFinish: (j) => {
      setJob(j);
      if (j.status === "DONE") {
        setCandidates(j.candidates);
        setSelected(initialSelection(j.candidates));
        setStep({ name: "review" });
      } else {
        setStep({ name: "input", mode: "text", failed: j.failureReason ?? "콘텐츠를 열 수 없어요." });
      }
    },
    onError: () => setStep({ name: "input", mode: "url", failed: undefined }),
  });

  const begin = (input: { url: string } | { text: string }) =>
    start.mutate(input, {
      onSuccess: (j) => {
        setJob(j);
        setJobId(j.id);
        setStep({ name: "progress" });
      },
    });

  const sourceLabel = job?.source.type === "TEXT" ? "직접 입력한 텍스트에서 가져옴" : "블로그 링크에서 가져옴";

  switch (step.name) {
    case "input":
      return (
        <InputStep
          mode={step.mode}
          failed={step.failed}
          onMode={(mode) => setStep({ name: "input", mode })}
          onSubmit={begin}
          submitting={start.isPending}
          error={start.error}
        />
      );
    case "progress":
      return (
        <ProgressStep
          step={job?.step ?? 0}
          total={job?.totalSteps ?? 4}
          onCancel={() => {
            if (jobId) importApi.cancel(jobId).catch(() => {});
            setStep({ name: "input", mode: "url" });
          }}
        />
      );
    case "review":
      return (
        <ReviewStep
          candidates={candidates}
          selected={selected}
          onToggle={(id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))}
          onFix={(tempId) => setStep({ name: "fix", tempId })}
          sourceUrl={job?.source.type === "URL" ? job!.source.value : null}
          onNext={() => setStep({ name: "collection" })}
          onBack={() => setStep({ name: "input", mode: "url" })}
        />
      );
    case "fix": {
      const cand = candidates.find((c) => c.tempId === step.tempId)!;
      return (
        <FixLocationStep
          candidate={cand}
          onBack={() => setStep({ name: "review" })}
          onApply={(place: Place) => {
            setCandidates(applyFix(candidates, cand.tempId, place, new Set(saved.map((s) => s.place.id))));
            setSelected((s) => [...new Set([...s, cand.tempId])]);
            setStep({ name: "review" });
          }}
          onExclude={() => {
            setCandidates(excludeCandidate(candidates, cand.tempId));
            setSelected((s) => s.filter((x) => x !== cand.tempId));
            setStep({ name: "review" });
          }}
        />
      );
    }
    case "collection": {
      const targets = saveTargets(candidates, selected);
      return (
        <CollectionStep
          targets={targets}
          saving={save.isPending}
          error={save.error}
          onBack={() => setStep({ name: "review" })}
          onSave={(collectionIds) =>
            save.mutate(
              { placeIds: targets.placeIds, collectionIds, source: { type: "LINK_IMPORT", refId: jobId, label: sourceLabel } },
              { onSuccess: (result) => setStep({ name: "done", result, collectionIds }) },
            )
          }
        />
      );
    }
    case "done":
      return (
        <DoneStep
          result={step.result}
          candidates={saveTargets(candidates, selected).candidates}
          skipped={candidates.filter((c) => !c.duplicateOf && (c.needsReview || !c.matchedPlace)).length}
          collectionIds={step.collectionIds}
        />
      );
  }
}
