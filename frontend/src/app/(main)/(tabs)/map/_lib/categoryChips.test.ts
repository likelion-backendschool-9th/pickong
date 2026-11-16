import { describe, expect, it } from "vitest";
import type { PlaceCategory } from "@/entities/place";
import { buildChipItems, moveChip, toggleChip, visibleCategories } from "./categoryChips";

const all: PlaceCategory[] = ["CAFE", "RESTAURANT", "SHOP", "PARK"];

describe("categoryChips", () => {
  it("보이는 칩을 저장 순서대로, 숨긴 칩은 뒤에 기본 순서로", () => {
    expect(buildChipItems(["SHOP", "CAFE"], all)).toEqual([
      { category: "SHOP", visible: true },
      { category: "CAFE", visible: true },
      { category: "RESTAURANT", visible: false },
      { category: "PARK", visible: false },
    ]);
  });

  it("목록에 없는 카테고리는 무시", () => {
    expect(buildChipItems(["ETC", "CAFE"], all).map((it) => it.category)).toEqual(["CAFE", "RESTAURANT", "SHOP", "PARK"]);
  });

  it("한 칸씩 옮기고, 끝에서는 그대로", () => {
    const items = buildChipItems(all, all);
    expect(moveChip(items, 1, -1).map((it) => it.category)).toEqual(["RESTAURANT", "CAFE", "SHOP", "PARK"]);
    expect(moveChip(items, 0, -1)).toBe(items);
    expect(moveChip(items, 3, 1)).toBe(items);
  });

  it("숨기면 저장 값에서 빠진다", () => {
    const items = toggleChip(buildChipItems(all, all), 2);
    expect(visibleCategories(items)).toEqual(["CAFE", "RESTAURANT", "PARK"]);
  });
});
