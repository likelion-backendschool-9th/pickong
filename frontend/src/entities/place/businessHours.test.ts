import { describe, expect, it } from "vitest";
import { openStateAt } from "./businessHours";

const hours = { open: "11:00", close: "18:00", closedDays: [1] };

describe("openStateAt", () => {
  it("영업시간 안이면 OPEN", () => {
    expect(openStateAt(hours, 6, 12 * 60)).toEqual({ type: "OPEN", closeAt: 18 * 60 });
  });
  it("종료 시각 이후면 AFTER_CLOSE", () => {
    expect(openStateAt(hours, 6, 18 * 60)).toEqual({ type: "AFTER_CLOSE", closeAt: 18 * 60 });
  });
  it("휴무일이면 시간과 관계없이 CLOSED_DAY", () => {
    expect(openStateAt(hours, 1, 12 * 60)).toEqual({ type: "CLOSED_DAY" });
  });
  it("영업시간 정보가 없으면 UNKNOWN", () => {
    expect(openStateAt(null, 6, 12 * 60)).toEqual({ type: "UNKNOWN" });
  });
});
