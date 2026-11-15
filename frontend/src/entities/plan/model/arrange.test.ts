import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import { arrangeInitial } from "./arrange";
import type { PlanItem } from "./types";

const place = (id: string): Place => ({
  id, name: id, address: "", region: "", location: { lat: 37.5446, lng: 127.0559 }, category: "CAFE",
  isCustom: false, visibility: "PUBLIC", description: "", imageUrl: null, businessHours: null,
});
const free = (id: string, stay: number): PlanItem => ({ id, kind: "FREE", place: place(id), stayMinutes: stay, visited: false, reservation: null });
const reserved = (id: string, start: string, end: string): PlanItem => ({
  id, kind: "RESERVATION", place: place(id), stayMinutes: 0, visited: false,
  reservation: { type: "SHOW", startTime: start, endTime: end, people: 2, reservationNumber: "", link: "", memo: "" },
});
const plan = { date: "2026-11-21", startTime: "10:00", endTime: "20:00", startPlace: place("start") };

describe("arrangeInitial", () => {
  it("예약 전 빈 시간에 들어가는 장소는 예약 앞에, 넘치는 장소는 예약 뒤에 둔다", () => {
    const items = arrangeInitial(plan, [reserved("lunch", "12:00", "13:00")], [free("a", 60), free("b", 90)]);
    expect(items.map((i) => i.id)).toEqual(["a", "lunch", "b"]);
  });
  it("예약은 시간 순서로 정렬한다", () => {
    const items = arrangeInitial(plan, [reserved("movie", "17:00", "19:00"), reserved("lunch", "12:00", "13:00")], []);
    expect(items.map((i) => i.id)).toEqual(["lunch", "movie"]);
  });
});
