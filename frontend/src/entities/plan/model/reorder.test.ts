import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import { fixedTimeOrderIssue, moveItem, removeItem, setFixedTime } from "./reorder";
import type { PlanItem } from "./types";

const item = (id: string, fixedTime: string | null = null): PlanItem => ({
  id,
  place: { id, name: id, location: { lat: 37.54, lng: 127.05 } } as unknown as Place,
  fixedTime,
  visited: false,
});

describe("plan reorder", () => {
  const list = [item("cafe"), item("movie", "19:00"), item("food", "18:00")];

  it("고정 시간이 있어도 순서를 옮길 수 있다", () => {
    expect(moveItem(list, 1, 2).map((i) => i.id)).toEqual(["cafe", "food", "movie"]);
  });

  it("범위를 벗어나면 그대로", () => {
    expect(moveItem(list, 0, 5)).toBe(list);
    expect(moveItem(list, 1, 1)).toBe(list);
  });

  it("고정 시간을 설정 · 해제한다", () => {
    expect(setFixedTime(list, "cafe", "11:00")[0].fixedTime).toBe("11:00");
    expect(setFixedTime(list, "movie", null)[1].fixedTime).toBeNull();
  });

  it("장소를 뺀다", () => expect(removeItem(list, "cafe").map((i) => i.id)).toEqual(["movie", "food"]));

  it("고정 시간이 순서와 어긋나면 알려 준다", () => {
    const issue = fixedTimeOrderIssue(list);
    expect(issue?.earlier.id).toBe("movie");
    expect(issue?.later.id).toBe("food");
    expect(fixedTimeOrderIssue(moveItem(list, 1, 2))).toBeNull();
  });

  it("고정 시간이 없는 곳은 순서 확인에서 빠진다", () => {
    expect(fixedTimeOrderIssue([item("a"), item("b", "12:00"), item("c")])).toBeNull();
  });
});
