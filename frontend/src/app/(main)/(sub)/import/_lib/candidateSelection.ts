import type { Place } from "@/entities/place";
import type { ImportCandidate } from "../_api/mapper";

/** 중복 후보를 뺀, 화면에 보여줄 후보 */
export const visibleCandidates = (cands: ImportCandidate[]) => cands.filter((c) => !c.duplicateOf);

export const isConfirmed = (c: ImportCandidate) => !!c.matchedPlace && !c.needsReview;

/** 기본 선택: 위치가 확인된 후보만. 불확실한 후보는 자동으로 선택하지 않습니다. */
export const initialSelection = (cands: ImportCandidate[]) =>
  visibleCandidates(cands).filter(isConfirmed).map((c) => c.tempId);

export function summarize(cands: ImportCandidate[]) {
  const visible = visibleCandidates(cands);
  return {
    found: cands.length,
    duplicates: cands.length - visible.length,
    unique: visible.length,
    confirmed: visible.filter(isConfirmed).length,
    needsReview: visible.filter((c) => !isConfirmed(c)).length,
  };
}

/** 사용자가 위치를 직접 확인한 후보 → 확인됨으로 바꾸고 선택합니다. */
export function applyFix(cands: ImportCandidate[], tempId: string, place: Place, savedPlaceIds: Set<string>) {
  return cands.map((c) =>
    c.tempId === tempId ? { ...c, matchedPlace: place, needsReview: false, alreadySaved: savedPlaceIds.has(place.id) } : c,
  );
}

export const excludeCandidate = (cands: ImportCandidate[], tempId: string) => cands.filter((c) => c.tempId !== tempId);

/**
 * 실제로 저장할 장소. 선택했고 위치가 확인된 후보만, 같은 장소는 한 번만.
 * 화면의 "N곳 저장" 숫자와 완료 화면의 결과 수가 같도록 이 함수 하나로 계산합니다.
 */
export function saveTargets(cands: ImportCandidate[], selected: string[]) {
  const seen = new Set<string>();
  const targets: ImportCandidate[] = [];
  for (const c of visibleCandidates(cands)) {
    if (!selected.includes(c.tempId) || !isConfirmed(c)) continue;
    if (seen.has(c.matchedPlace!.id)) continue;
    seen.add(c.matchedPlace!.id);
    targets.push(c);
  }
  return {
    candidates: targets,
    placeIds: targets.map((c) => c.matchedPlace!.id),
    newCount: targets.filter((c) => !c.alreadySaved).length,
    existingCount: targets.filter((c) => c.alreadySaved).length,
  };
}
