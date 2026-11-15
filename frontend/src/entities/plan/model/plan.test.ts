import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import { canDrag, insertReservation, moveItem, setStay } from "./reorder";
import { suggestForGap, suggestOrder } from "./suggest";
import { buildTimeline } from "./timeline";
import type { Leg, PlanItem } from "./types";

const place = (id: string, hours: Place["businessHours"] = null, lat = 37.5446, lng = 127.0559): Place => ({
  id,
  name: id,
  address: "",
  region: "",
  location: { lat, lng },
  category: "CAFE",
  isCustom: false,
  visibility: "PUBLIC",
  description: "",
  imageUrl: null,
  businessHours: hours,
});

const free = (id: string, stay: number, hours: Place["businessHours"] = null): PlanItem => ({
  id,
  kind: "FREE",
  place: place(id, hours),
  stayMinutes: stay,
  visited: false,
  reservation: null,
});

const reserved = (id: string, start: string, end: string): PlanItem => ({
  id,
  kind: "RESERVATION",
  place: place(id),
  stayMinutes: 0,
  visited: false,
  reservation: { type: "SHOW", startTime: start, endTime: end, people: 2, reservationNumber: "", link: "", memo: "" },
});

// 2026-11-21은 토요일
const plan = { date: "2026-11-21", startTime: "10:00", endTime: "20:00", startPlace: place("start") };
const legs = (n: number, minutes = 10): Leg[] => Array.from({ length: n }, () => ({ minutes, isEstimate: false }));

describe("buildTimeline", () => {
  it("이동 시간과 체류 시간으로 도착·출발 시각을 계산한다", () => {
    const items = [free("a", 60), free("b", 30)];
    const t = buildTimeline(plan, items, legs(2));
    expect(t.entries.map((e) => [e.arriveAt, e.departAt])).toEqual([
      [610, 670],
      [680, 710],
    ]);
    expect(t.warningCount).toBe(0);
  });

  it("확정 예약 전 일찍 도착하면 빈 시간이 생기고 예약 시간은 그대로다", () => {
    const items = [free("a", 30), reserved("r", "12:30", "13:40")];
    const t = buildTimeline(plan, items, legs(2));
    const r = t.entries[1];
    expect(r.startAt).toBe(12 * 60 + 30);
    expect(r.departAt).toBe(13 * 60 + 40);
    expect(r.waitMinutes).toBe(12 * 60 + 30 - (10 * 60 + 50));
  });

  it("다음 확정 예약에 늦으면 지연 시간과 원인 장소를 알려주고 예약 시간은 바꾸지 않는다", () => {
    const items = [free("a", 420), reserved("movie", "17:00", "19:00")];
    const t = buildTimeline(plan, items, legs(2));
    const w = t.entries[1].warnings[0];
    expect(w).toEqual({ type: "LATE_FOR_RESERVATION", arriveAt: 17 * 60 + 20, startAt: 17 * 60, lateMinutes: 20, causeItemId: "a" });
    expect(t.entries[1].startAt).toBe(17 * 60);
  });

  it("영업 종료 이후 도착하면 경고한다", () => {
    const items = [reserved("movie", "17:00", "19:00"), free("shop", 30, { open: "11:00", close: "19:00", closedDays: [] })];
    const t = buildTimeline(plan, items, legs(2));
    expect(t.entries[1].warnings).toEqual([{ type: "AFTER_CLOSE", arriveAt: 19 * 60 + 10, closeAt: 19 * 60, lateMinutes: 10 }]);
  });

  it("휴무일이면 경고한다", () => {
    const items = [free("shop", 30, { open: "11:00", close: "19:00", closedDays: [6] })];
    expect(buildTimeline(plan, items, legs(1)).entries[0].warnings).toEqual([{ type: "CLOSED_DAY" }]);
  });

  it("오픈 전에 도착하면 오픈 시각부터 체류한다", () => {
    const items = [free("cafe", 60, { open: "11:00", close: "21:00", closedDays: [] })];
    const e = buildTimeline(plan, items, legs(1)).entries[0];
    expect(e.startAt).toBe(11 * 60);
    expect(e.departAt).toBe(12 * 60);
  });

  it("이동 시간이 없으면 추정치를 쓰고 추정 표시를 남긴다", () => {
    const t = buildTimeline(plan, [free("a", 30)]);
    expect(t.entries[0].leg.isEstimate).toBe(true);
  });
});

describe("moveItem", () => {
  const items = [free("a", 30), reserved("r", "12:00", "13:00"), free("b", 30)];

  it("확정 예약은 옮길 수 없다", () => {
    expect(canDrag(items[1])).toBe(false);
    expect(moveItem(items, 1, 0)).toBe(items);
  });

  it("자유 일정은 예약 뒤로 옮길 수 있고 예약 내용은 바뀌지 않는다", () => {
    const next = moveItem(items, 0, 2);
    expect(next.map((i) => i.id)).toEqual(["r", "b", "a"]);
    expect(next[0].reservation).toBe(items[1].reservation);
  });

  it("체류 시간 수정은 자유 일정에만 적용된다", () => {
    const next = setStay(items, "r", 999);
    expect(next[1].stayMinutes).toBe(0);
  });
});

describe("insertReservation", () => {
  it("예약 시작 시간 순서에 맞는 위치에 넣는다", () => {
    const items = [free("a", 30), reserved("lunch", "12:00", "13:00"), reserved("movie", "17:00", "19:00")];
    const next = insertReservation(items, reserved("tea", "15:00", "16:00"));
    expect(next.map((i) => i.id)).toEqual(["a", "lunch", "tea", "movie"]);
  });
});

describe("suggestOrder", () => {
  it("영업 종료 경고를 없애는 순서를 제안한다", () => {
    const closesEarly = free("early", 30, { open: "10:00", close: "12:00", closedDays: [] });
    const items = [free("long", 120), closesEarly];
    const suggestion = suggestOrder(plan, items);
    expect(suggestion?.items.map((i) => i.id)).toEqual(["early", "long"]);
    expect(suggestion?.timeline.warningCount).toBe(0);
  });

  it("확정 예약 위치는 바꾸지 않는다", () => {
    const items = [free("long", 200), reserved("r", "13:00", "14:00"), free("early", 30, { open: "10:00", close: "12:00", closedDays: [] })];
    const suggestion = suggestOrder(plan, items);
    expect(suggestion?.items[1].id).toBe("r");
  });

  it("문제를 해결하는 순서 중 가장 적게 바꾸는 순서를 고른다", () => {
    const early = free("early", 30, { open: "10:00", close: "12:00", closedDays: [] });
    const items = [free("a", 30), free("b", 30), free("long", 120), early];
    expect(suggestOrder(plan, items)?.items.map((i) => i.id)).toEqual(["a", "b", "early", "long"]);
  });

  it("더 나은 순서가 없으면 null", () => {
    expect(suggestOrder(plan, [free("a", 30), free("b", 30)])).toBeNull();
  });
});

describe("suggestForGap", () => {
  it("빈 시간 안에 다녀올 수 있고 영업 중인 장소만 추천한다", () => {
    const open = place("open", { open: "10:00", close: "20:00", closedDays: [] });
    const closed = place("closed", { open: "18:00", close: "22:00", closedDays: [] });
    const result = suggestForGap([open, closed], {
      from: place("from"),
      to: place("to"),
      startAt: 14 * 60,
      endAt: 17 * 60,
      date: "2026-11-21",
    });
    expect(result.map((r) => r.place.id)).toEqual(["open"]);
  });
});
