import { describe, expect, it } from "vitest";
import type { Place, PlaceCluster, PlacesInBounds } from "@/entities/place";
import { nearbyToShow, type NearbyOptions } from "./nearbyToShow";

const place = (id: string, category = "CAFE"): Place => ({ id, name: id, category }) as unknown as Place;

const cluster = { id: "c", location: { lat: 37.5, lng: 127 }, count: 12 } as unknown as PlaceCluster;

const nearby: PlacesInBounds = { places: [place("a"), place("b", "SHOP"), place("saved")], clusters: [cluster] };

const options = (overrides: Partial<NearbyOptions> = {}): NearbyOptions => ({
  savedOnly: false,
  savedPlaceIds: new Set(["saved"]),
  category: "ALL",
  picked: null,
  focus: null,
  ...overrides,
});

const ids = (result: PlacesInBounds) => result.places.map((p) => p.id);

describe("nearbyToShow", () => {
  it("저장한 곳만 보기를 켜면 주변 장소와 묶음을 모두 뺀다", () => {
    const result = nearbyToShow(nearby, options({ savedOnly: true, picked: place("a"), focus: place("x") }));
    expect(result.places).toEqual([]);
    expect(result.clusters).toEqual([]);
  });

  it("끄면 저장한 장소를 뺀 주변 장소와 묶음을 보여 준다", () => {
    const result = nearbyToShow(nearby, options());
    expect(ids(result)).toEqual(["a", "b"]);
    expect(result.clusters).toEqual([cluster]);
  });

  it("카테고리를 고르면 그 카테고리의 주변 장소만", () => {
    expect(ids(nearbyToShow(nearby, options({ category: "SHOP" })))).toEqual(["b"]);
  });

  it("고른 주변 장소와 검색에서 고른 장소는 조회 결과에 없어도 남긴다", () => {
    const result = nearbyToShow(nearby, options({ picked: place("p"), focus: place("f") }));
    expect(ids(result)).toEqual(["a", "b", "p", "f"]);
  });

  it("고른 장소가 이미 있거나 저장한 장소면 다시 넣지 않는다", () => {
    expect(ids(nearbyToShow(nearby, options({ picked: place("a"), focus: place("saved") })))).toEqual(["a", "b"]);
  });

  it("조회 결과를 바꾸지 않는다", () => {
    nearbyToShow(nearby, options({ picked: place("p") }));
    expect(ids(nearby)).toEqual(["a", "b", "saved"]);
  });
});
