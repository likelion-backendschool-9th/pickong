import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import { planShareText } from "./share";
import type { Plan } from "./types";

const place = (name: string) => ({ id: name, name }) as unknown as Place;

describe("planShareText", () => {
  it("제목 · 날짜 · 지역과 순서대로 장소, 고정 시간을 적는다", () => {
    const plan: Plan = {
      id: "p",
      title: "성수에서 보내는 토요일",
      date: "2026-11-21",
      region: "성수동",
      memo: "",
      status: "UPCOMING",
      items: [
        { id: "a", place: place("베이커리 소금"), fixedTime: null, visited: false },
        { id: "b", place: place("식당 A"), fixedTime: "12:30", visited: false },
      ],
    };
    expect(planShareText(plan)).toBe("성수에서 보내는 토요일 · 11.21 (토) · 성수동\n1. 베이커리 소금\n2. 식당 A (12:30)");
  });
});
