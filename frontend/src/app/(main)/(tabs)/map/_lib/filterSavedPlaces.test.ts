import { describe, expect, it } from "vitest";
import type { SavedPlace } from "@/entities/place";
import { EMPTY_FILTER, filterSavedPlaces } from "./filterSavedPlaces";

const sp = (id: string, category: string, collectionIds: string[], visitStatus = "WANT_TO_GO"): SavedPlace =>
  ({
    id,
    place: { id, name: id === "a" ? "카페 온유" : "소품샵 모모", region: "성수동", address: "", category },
    collectionIds,
    visitStatus,
  }) as unknown as SavedPlace;

const list = [sp("a", "CAFE", ["c1"]), sp("b", "SHOP", [], "VISITED")];

describe("filterSavedPlaces", () => {
  it("조건이 없으면 전부", () => expect(filterSavedPlaces(list, EMPTY_FILTER)).toHaveLength(2));
  it("검색어는 띄어쓰기를 무시한다", () =>
    expect(filterSavedPlaces(list, { ...EMPTY_FILTER, query: "카페온" }).map((s) => s.id)).toEqual(["a"]));
  it("컬렉션 · 카테고리 · 방문 상태로 거른다", () => {
    expect(filterSavedPlaces(list, { ...EMPTY_FILTER, collectionId: "c1" }).map((s) => s.id)).toEqual(["a"]);
    expect(filterSavedPlaces(list, { ...EMPTY_FILTER, collectionId: "NONE" }).map((s) => s.id)).toEqual(["b"]);
    expect(filterSavedPlaces(list, { ...EMPTY_FILTER, category: "SHOP" }).map((s) => s.id)).toEqual(["b"]);
    expect(filterSavedPlaces(list, { ...EMPTY_FILTER, visit: "VISITED" }).map((s) => s.id)).toEqual(["b"]);
  });
});
