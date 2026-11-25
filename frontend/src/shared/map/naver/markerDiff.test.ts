import { describe, expect, it } from "vitest";
import type { MapMarker } from "../types";
import { diffMarkers, MAX_PINS_WITH_TITLE, planMarkers, type MarkerPlan } from "./markerDiff";

const poi = (id: string, lat = 37.5): MapMarker => ({ id, position: { lat, lng: 127 }, title: id, tone: "poi" });
const pin = (id: string): MapMarker => ({ id, position: { lat: 37.5, lng: 127 }, title: id, tone: "primary" });

const drawn = (plans: MarkerPlan[]) => new Map(plans.map((p) => [p.key, { signature: p.signature }]));
const ids = (plans: MarkerPlan[]) => plans.map((p) => p.marker.id);
const diff = (before: MarkerPlan[], after: MarkerPlan[]) => {
  const { remove, add } = diffMarkers(drawn(before), after);
  return { remove: before.filter((p) => remove.includes(p.key)).map((p) => p.marker.id), add: ids(add) };
};

describe("planMarkers", () => {
  it("일반 장소는 선택했을 때만 이름표를 보여 준다", () => {
    const [a, b] = planMarkers([poi("a"), poi("b")], "b");
    expect([a.showTitle, b.showTitle]).toEqual([false, true]);
  });

  it("핀이 기준 개수 이하면 모두 이름표를 보여 준다", () => {
    const pins = Array.from({ length: MAX_PINS_WITH_TITLE }, (_, i) => pin(`p${i}`));
    expect(planMarkers(pins, null).every((p) => p.showTitle)).toBe(true);
  });

  it("핀이 기준 개수를 넘으면 선택한 핀만 이름표를 보여 준다", () => {
    const pins = Array.from({ length: MAX_PINS_WITH_TITLE + 1 }, (_, i) => pin(`p${i}`));
    expect(ids(planMarkers(pins, "p2").filter((p) => p.showTitle))).toEqual(["p2"]);
  });

  it("일반 장소는 핀 개수에 세지 않는다", () => {
    const plans = planMarkers([pin("mine"), ...Array.from({ length: 50 }, (_, i) => poi(`x${i}`))], null);
    expect(ids(plans.filter((p) => p.showTitle))).toEqual(["mine"]);
  });

  it("이름이 없으면 선택해도 이름표를 보여 주지 않는다", () => {
    expect(planMarkers([{ id: "a", position: { lat: 37.5, lng: 127 } }], "a")[0].showTitle).toBe(false);
  });

  it("id가 같은 마커도 key가 겹치지 않는다", () => {
    const keys = planMarkers([pin("a"), pin("a")], null).map((p) => p.key);
    expect(new Set(keys).size).toBe(2);
  });
});

describe("diffMarkers", () => {
  it("내용이 같으면 배열이 새로 만들어져도 다시 그리지 않는다", () => {
    expect(diff(planMarkers([poi("a"), pin("b")], "a"), planMarkers([poi("a"), pin("b")], "a"))).toEqual({
      remove: [],
      add: [],
    });
  });

  it("처음에는 모두 그린다", () => {
    expect(diff([], planMarkers([poi("a"), poi("b")], null))).toEqual({ remove: [], add: ["a", "b"] });
  });

  it("겹치지 않는 마커만 추가하고 지운다", () => {
    const before = planMarkers([poi("a"), poi("b")], null);
    const after = planMarkers([poi("b"), poi("c")], null);
    expect(diff(before, after)).toEqual({ remove: ["a"], add: ["c"] });
  });

  it("선택이 바뀌면 이전 · 새 선택 두 개만 다시 그린다", () => {
    const list = Array.from({ length: 1000 }, (_, i) => poi(`x${i}`));
    expect(diff(planMarkers(list, "x1"), planMarkers(list, "x5"))).toEqual({
      remove: ["x1", "x5"],
      add: ["x1", "x5"],
    });
  });

  it("위치가 바뀐 마커를 다시 그린다", () => {
    expect(diff(planMarkers([poi("a")], null), planMarkers([poi("a", 37.6)], null))).toEqual({
      remove: ["a"],
      add: ["a"],
    });
  });

  it("빈 목록이면 모두 지운다", () => {
    expect(diff(planMarkers([poi("a"), pin("b")], null), [])).toEqual({ remove: ["a", "b"], add: [] });
  });
});
