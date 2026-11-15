import { describe, expect, it } from "vitest";
import type { PlanItem } from "@/entities/plan";
import { describeWarning, targetItemId } from "./warningText";

const item = (id: string, name: string, kind: "FREE" | "RESERVATION" = "FREE") =>
  ({ id, kind, place: { name } }) as unknown as PlanItem;
const gyeol = item("g", "전시 공간 결");
const movie = item("m", "영화관 B", "RESERVATION");

describe("describeWarning", () => {
  it("영업 종료: 장소 · 종료 시각 · 도착 시각 · 지연을 모두 보여준다", () => {
    const d = describeWarning({ type: "AFTER_CLOSE", arriveAt: 19 * 60 + 12, closeAt: 19 * 60, lateMinutes: 12 }, gyeol, [gyeol]);
    expect(d.detail).toContain("19:00");
    expect(d.detail).toContain("19:12");
    expect(d.detail).toContain("12분");
  });
  it("예약 지연: 원인 장소와 지연 시간을 보여주고, 손볼 대상은 원인 장소다", () => {
    const w = { type: "LATE_FOR_RESERVATION" as const, arriveAt: 17 * 60 + 12, startAt: 17 * 60, lateMinutes: 12, causeItemId: "g" };
    const d = describeWarning(w, movie, [gyeol, movie]);
    expect(d.title).toBe("17:00 영화관 B 예약에 늦을 수 있어요");
    expect(d.detail).toContain("전시 공간 결에서 출발하면 17:12");
    expect(targetItemId(w, movie)).toBe("g");
  });
});
