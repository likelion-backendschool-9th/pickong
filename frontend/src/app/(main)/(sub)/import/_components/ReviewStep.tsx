"use client";

import { AlertTriangle, ExternalLink } from "lucide-react";
import { Badge } from "@/shared/ui/display";
import { Button } from "@/shared/ui/Button";
import { CheckMark } from "@/shared/ui/controls";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { ImportCandidate } from "../_api/mapper";
import { isConfirmed, saveTargets, summarize, visibleCandidates } from "../_lib/candidateSelection";

export function ReviewStep({
  candidates,
  selected,
  onToggle,
  onFix,
  sourceUrl,
  onNext,
  onBack,
}: {
  candidates: ImportCandidate[];
  selected: string[];
  onToggle: (id: string) => void;
  onFix: (tempId: string) => void;
  sourceUrl: string | null;
  onNext: () => void;
  onBack: () => void;
}) {
  const s = summarize(candidates);
  const targets = saveTargets(candidates, selected);
  const duplicates = candidates.filter((c) => c.duplicateOf);

  return (
    <Screen>
      <TopBar title="가져온 장소 확인" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[22px] font-bold">
          {s.found > 0 ? `후보 ${s.found}곳을 찾았어요` : "장소를 찾지 못했어요"}
        </h2>
        <p className="mt-1.5 text-[13px] text-ink-sub">
          확인된 {s.confirmed}곳 선택{s.needsReview ? ` · 위치 확인 ${s.needsReview}곳` : ""}
          {s.duplicates ? ` · 중복 ${s.duplicates}곳 제외` : ""}
        </p>
        {sourceUrl && (
          <a href={sourceUrl} target="_blank" rel="noreferrer noopener" className="mt-2 inline-flex items-center gap-1 text-[12px] font-bold text-primary-deep">
            원본 링크 보기 <ExternalLink size={12} />
          </a>
        )}

        <ul className="mt-5 space-y-2">
          {visibleCandidates(candidates).map((c) => {
            const confirmed = isConfirmed(c);
            const checked = selected.includes(c.tempId) && confirmed;
            return (
              <li key={c.tempId} className={confirmed ? "" : "rounded-card bg-warning-soft"}>
                <div className="flex items-start gap-3 px-3 py-3">
                  <button
                    type="button"
                    role="checkbox"
                    aria-checked={checked}
                    aria-disabled={!confirmed}
                    aria-label={`${c.matchedPlace?.name ?? c.extractedName} 선택`}
                    onClick={() => confirmed && onToggle(c.tempId)}
                    className="pt-0.5"
                  >
                    <CheckMark checked={checked} disabled={!confirmed} round />
                  </button>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[14px] font-bold">{c.matchedPlace?.name ?? c.extractedName}</span>
                      {c.alreadySaved && confirmed && <Badge tone="neutral">이미 저장됨</Badge>}
                    </div>
                    {confirmed ? (
                      <p className="mt-0.5 text-[12px] text-ink-sub">
                        {c.matchedPlace!.region} · {c.alreadySaved ? "새로 만들지 않고 컬렉션에 연결" : "지도 위치 확인됨"}
                      </p>
                    ) : (
                      <>
                        <p className="mt-0.5 flex items-center gap-1 text-[12px] text-warning">
                          <AlertTriangle size={12} /> 위치 확인 필요 · 비슷한 장소가 여러 곳 있어요
                        </p>
                        <button type="button" onClick={() => onFix(c.tempId)} className="mt-1.5 text-[13px] font-bold text-ink">
                          위치 확인하기 →
                        </button>
                      </>
                    )}
                    {confirmed && c.extractedName !== c.matchedPlace!.name && (
                      <button type="button" onClick={() => onFix(c.tempId)} className="mt-1 text-[12px] text-ink-sub underline">
                        원문 ‘{c.extractedName}’ · 위치 수정
                      </button>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>

        {duplicates.length > 0 && (
          <p className="mt-4 text-[12px] text-ink-faint">
            중복 제외 · {duplicates.map((d) => d.extractedName).join(", ")} (본문에서 여러 번 등장)
          </p>
        )}
        <p className="mt-2 text-[12px] text-ink-faint">위치를 확인하지 않은 장소는 자동으로 저장하지 않아요.</p>
      </div>
      <BottomCta>
        <Button block disabled={targets.placeIds.length === 0} onClick={onNext}>
          선택한 {targets.placeIds.length}곳 저장하기
        </Button>
      </BottomCta>
    </Screen>
  );
}
