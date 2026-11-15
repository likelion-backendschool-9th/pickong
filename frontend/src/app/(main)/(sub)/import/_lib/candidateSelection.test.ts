import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import type { ImportCandidate } from "../_api/mapper";
import { applyFix, excludeCandidate, initialSelection, saveTargets, summarize } from "./candidateSelection";

const place = (id: string) => ({ id, name: id }) as Place;
const cand = (tempId: string, p: Place | null, extra: Partial<ImportCandidate> = {}): ImportCandidate => ({
  tempId,
  extractedName: tempId,
  extractedAddress: "",
  matchedPlace: p,
  alternatives: [],
  confidence: p ? 0.9 : 0.4,
  needsReview: !p,
  duplicateOf: null,
  alreadySaved: false,
  ...extra,
});

// 기존 예시: 후보 4개 → 중복 제거 후 3곳 → 확인된 2곳만 기본 선택
const cands = [
  cand("onyu", place("p-onyu"), { alreadySaved: true }),
  cand("momo", place("p-momo")),
  cand("noeul", null, { alternatives: [place("p-view")] }),
  cand("onyu-dup", place("p-onyu"), { duplicateOf: "onyu" }),
];

describe("링크 후보 선택", () => {
  it("요약: 후보 4 · 중복 1 · 실제 3곳 · 확인 2 · 확인 필요 1", () => {
    expect(summarize(cands)).toEqual({ found: 4, duplicates: 1, unique: 3, confirmed: 2, needsReview: 1 });
  });

  it("위치가 확인된 후보만 기본 선택한다", () => {
    expect(initialSelection(cands)).toEqual(["onyu", "momo"]);
  });

  it("불확실한 후보는 선택해도 저장 대상이 아니다", () => {
    expect(saveTargets(cands, ["onyu", "momo", "noeul"]).placeIds).toEqual(["p-onyu", "p-momo"]);
  });

  it("저장 수에서 새 장소와 기존 장소를 구분한다", () => {
    expect(saveTargets(cands, ["onyu", "momo"])).toMatchObject({ newCount: 1, existingCount: 1 });
  });

  it("사용자가 위치를 확인하면 저장 대상에 들어간다", () => {
    const fixed = applyFix(cands, "noeul", place("p-view"), new Set());
    const t = saveTargets(fixed, ["onyu", "momo", "noeul"]);
    expect(t.placeIds).toEqual(["p-onyu", "p-momo", "p-view"]);
    expect(t).toMatchObject({ newCount: 2, existingCount: 1 });
  });

  it("같은 장소로 고치면 한 번만 저장한다", () => {
    const fixed = applyFix(cands, "noeul", place("p-momo"), new Set());
    expect(saveTargets(fixed, ["onyu", "momo", "noeul"]).placeIds).toEqual(["p-onyu", "p-momo"]);
  });

  it("제외한 후보는 사라진다", () => {
    expect(summarize(excludeCandidate(cands, "noeul")).unique).toBe(2);
  });
});
