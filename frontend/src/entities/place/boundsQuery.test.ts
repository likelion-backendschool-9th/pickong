import { describe, expect, it } from "vitest";
import {
  isPlaceBoundsQueryAllowed,
  MAX_PLACE_BOUNDS_SPAN_METERS,
  placeBoundsCacheKey,
  placeBoundsSpanMeters,
  quantizePlaceBounds,
} from "./boundsQuery";

/** 성수 인근 동네 뷰 (~1–2km) */
const neighborhood = {
  north: 37.55,
  south: 37.54,
  east: 127.06,
  west: 127.04,
};

/** 서울 대부분을 덮는 광역 (~20km+) */
const cityWide = {
  north: 37.7,
  south: 37.4,
  east: 127.2,
  west: 126.8,
};

describe("quantizePlaceBounds", () => {
  it("크게 확대해도 화면을 모두 덮고 크기가 0이 되지 않는다", () => {
    const view = { north: 37.49792, south: 37.4978, east: 127.02762, west: 127.02751 };
    const q = quantizePlaceBounds(view);
    expect(q.west).toBeLessThanOrEqual(view.west);
    expect(q.south).toBeLessThanOrEqual(view.south);
    expect(q.east).toBeGreaterThanOrEqual(view.east);
    expect(q.north).toBeGreaterThanOrEqual(view.north);
    expect(q.east).toBeGreaterThan(q.west);
    expect(q.north).toBeGreaterThan(q.south);
  });

  it("이미 셋째 자리인 값은 더 넓히지 않는다", () => {
    const b = { north: 37.545, south: 37.54, east: 127.056, west: 127.04 };
    expect(quantizePlaceBounds(b)).toEqual(b);
  });

  it("소수 셋째 자리로 바깥쪽으로 넓힌다", () => {
    expect(
      quantizePlaceBounds({
        north: 37.54461,
        south: 37.54009,
        east: 127.05591,
        west: 127.04049,
      }),
    ).toEqual({
      north: 37.545,
      south: 37.54,
      east: 127.056,
      west: 127.04,
    });
  });

  it("미세하게 다른 범위도 같은 값으로 묶인다", () => {
    const a = quantizePlaceBounds({
      north: 37.5446,
      south: 37.5401,
      east: 127.0559,
      west: 127.0404,
    });
    const b = quantizePlaceBounds({
      north: 37.54461,
      south: 37.54012,
      east: 127.05592,
      west: 127.04041,
    });
    expect(a).toEqual(b);
  });
});

describe("placeBoundsCacheKey", () => {
  it("west,south,east,north 순 문자열을 만든다", () => {
    expect(placeBoundsCacheKey(neighborhood)).toBe("127.04,37.54,127.06,37.55");
  });

  it("반올림 후 같은 키면 동일 영역으로 본다", () => {
    const a = placeBoundsCacheKey({
      north: 37.5446,
      south: 37.5401,
      east: 127.0559,
      west: 127.0404,
    });
    const b = placeBoundsCacheKey({
      north: 37.54461,
      south: 37.54012,
      east: 127.05592,
      west: 127.04041,
    });
    expect(a).toBe(b);
  });
});

describe("placeBoundsSpanMeters", () => {
  it("동네 뷰는 한 변이 수 km 수준이다", () => {
    const { width, height } = placeBoundsSpanMeters(neighborhood);
    expect(height).toBeGreaterThan(1_000);
    expect(height).toBeLessThan(2_000);
    expect(width).toBeGreaterThan(1_500);
    expect(width).toBeLessThan(3_000);
  });

  it("광역 뷰는 한 변이 제한(15km)을 넘는다", () => {
    const { width, height } = placeBoundsSpanMeters(cityWide);
    expect(height).toBeGreaterThan(MAX_PLACE_BOUNDS_SPAN_METERS);
    expect(width).toBeGreaterThan(MAX_PLACE_BOUNDS_SPAN_METERS);
  });
});

describe("isPlaceBoundsQueryAllowed", () => {
  it("동네 범위는 조회를 허용한다", () => {
    expect(isPlaceBoundsQueryAllowed(neighborhood)).toBe(true);
  });

  it("지나치게 넓은 범위는 조회를 막는다", () => {
    expect(isPlaceBoundsQueryAllowed(cityWide)).toBe(false);
  });

  it("한 변만 임계값을 넘어도 막는다", () => {
    // 세로는 짧고 가로만 넓은 띠
    const wideStrip = {
      north: 37.55,
      south: 37.54,
      east: 127.3,
      west: 126.9,
    };
    expect(isPlaceBoundsQueryAllowed(wideStrip)).toBe(false);
  });

  it("임계값을 인자로 바꿀 수 있다", () => {
    expect(isPlaceBoundsQueryAllowed(neighborhood, 500)).toBe(false);
    expect(isPlaceBoundsQueryAllowed(neighborhood, 50_000)).toBe(true);
  });
});
