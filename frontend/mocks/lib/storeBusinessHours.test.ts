import { describe, expect, it } from "vitest";
import type { PlaceCategory } from "../fixtures/types.ts";
import { storeBusinessHours, toHHmm } from "./storeBusinessHours.ts";

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$|^24:00$/;
const minutes = (hhmm: string) => {
  const [hour, minute] = hhmm.split(":").map(Number);
  return hour * 60 + minute;
};

const samples: [PlaceCategory, string][] = [
  ["CAFE", "카페"],
  ["CAFE", "빵/도넛"],
  ["RESTAURANT", "백반/한정식"],
  ["RESTAURANT", "요리 주점"],
  ["SHOP", "꽃집"],
  ["EXHIBITION", "예술품 소매업"],
];

describe("storeBusinessHours", () => {
  it("모든 값이 24시간 표기이고 시작 < 종료 ≤ 24:00, 30분 단위", () => {
    for (const [category, description] of samples) {
      for (let i = 0; i < 500; i++) {
        const hours = storeBusinessHours(`s-${category}-${i}`, category, description);
        expect(hours.open).toMatch(HHMM);
        expect(hours.close).toMatch(HHMM);
        expect(minutes(hours.open)).toBeLessThan(minutes(hours.close));
        expect(minutes(hours.close)).toBeLessThanOrEqual(24 * 60);
        expect(minutes(hours.open) % 30).toBe(0);
        expect(minutes(hours.close) % 30).toBe(0);
        expect(hours.closedDays.length).toBeLessThanOrEqual(1);
        for (const day of hours.closedDays) expect(day).toBeGreaterThanOrEqual(0);
        for (const day of hours.closedDays) expect(day).toBeLessThanOrEqual(6);
      }
    }
  });

  it("같은 장소는 항상 같은 영업시간 (서버를 다시 켜도 유지)", () => {
    expect(storeBusinessHours("s-MA0001", "CAFE", "카페")).toEqual(storeBusinessHours("s-MA0001", "CAFE", "카페"));
  });

  it("업종에 맞는 범위: 카페는 07~11시 오픈, 주점은 16~18시 오픈, 갤러리는 10~19시 안", () => {
    for (let i = 0; i < 200; i++) {
      const cafe = minutes(storeBusinessHours(`s-${i}`, "CAFE", "카페").open);
      expect(cafe).toBeGreaterThanOrEqual(7 * 60);
      expect(cafe).toBeLessThanOrEqual(11 * 60);
      const bar = minutes(storeBusinessHours(`s-${i}`, "RESTAURANT", "요리 주점").open);
      expect(bar).toBeGreaterThanOrEqual(16 * 60);
      expect(bar).toBeLessThanOrEqual(18 * 60);
      const gallery = storeBusinessHours(`s-${i}`, "EXHIBITION", "예술품 소매업");
      expect(minutes(gallery.open)).toBeGreaterThanOrEqual(10 * 60);
      expect(minutes(gallery.close)).toBeLessThanOrEqual(19 * 60);
    }
  });

  it("장소마다 값이 고르게 달라짐 (모두 같은 시간이 아님)", () => {
    const opens = new Set(Array.from({ length: 100 }, (_, i) => storeBusinessHours(`s-${i}`, "CAFE", "카페").open));
    expect(opens.size).toBeGreaterThan(3);
  });
});

describe("toHHmm", () => {
  it("분을 HH:mm으로, 자정은 24:00으로", () => {
    expect(toHHmm(0)).toBe("00:00");
    expect(toHHmm(9 * 60 + 30)).toBe("09:30");
    expect(toHHmm(24 * 60)).toBe("24:00");
  });
});
