import { describe, expect, it } from "vitest";
import { toPlaceCluster } from "./mapper";

describe("toPlaceCluster", () => {
  const dto = { lat: 37.5, lng: 127, count: 3, bounds: { west: 126.9, south: 37.4, east: 127.1, north: 37.6 } };

  it("좌표 · 개수 · 범위를 옮긴다", () => {
    expect(toPlaceCluster(dto)).toMatchObject({
      location: { lat: 37.5, lng: 127 },
      count: 3,
      bounds: { west: 126.9, south: 37.4, east: 127.1, north: 37.6 },
    });
  });

  it("범위가 같으면 id가 같고, 다르면 다르다", () => {
    expect(toPlaceCluster(dto).id).toBe(toPlaceCluster({ ...dto, count: 9 }).id);
    expect(toPlaceCluster(dto).id).not.toBe(toPlaceCluster({ ...dto, bounds: { ...dto.bounds, west: 126 } }).id);
  });
});
