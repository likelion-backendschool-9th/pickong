import { describe, expect, it } from "vitest";
import { formatClusterCount, isStackedCluster } from "./cluster";

describe("isStackedCluster", () => {
  it("좌표가 모두 같으면 목록으로 본다", () => {
    expect(isStackedCluster({ bounds: { west: 127, east: 127, south: 37.5, north: 37.5 } })).toBe(true);
  });

  it("몇 미터 안에 모여 있으면 목록으로 본다", () => {
    expect(isStackedCluster({ bounds: { west: 127, east: 127.00005, south: 37.5, north: 37.50003 } })).toBe(true);
  });

  it("떨어져 있으면 확대해서 나눈다", () => {
    expect(isStackedCluster({ bounds: { west: 127, east: 127.001, south: 37.5, north: 37.5 } })).toBe(false);
  });
});

describe("formatClusterCount", () => {
  it.each([
    [2, "2"],
    [999, "999"],
    [1000, "1천"],
    [1299, "1.2천"],
    [9999, "9.9천"],
    [10_000, "1만"],
    [34_090, "3.4만"],
  ])("%i → %s", (count, label) => {
    expect(formatClusterCount(count)).toBe(label);
  });
});
