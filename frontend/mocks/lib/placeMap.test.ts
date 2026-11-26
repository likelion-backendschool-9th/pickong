import { describe, expect, it } from "vitest";
import { cellSize, clusterPlaces, inBounds, listPlaces, MAX_LIST, MAX_SPOTS } from "./placeMap.ts";

const BOUNDS = { west: 127.0, south: 37.5, east: 127.1, north: 37.6 };

const point = (id: string, lat: number, lng: number, name = id) => ({ id, name, lat, lng });

/** 범위 안에 서로 다른 좌표 n곳을 고르게 깝니다 */
const spread = (n: number) =>
  Array.from({ length: n }, (_, i) => point(`p${i}`, 37.5 + ((i % 20) + 0.5) * 0.005, 127.0 + (Math.floor(i / 20) + 0.5) * 0.005));

describe("cellSize", () => {
  it("2의 거듭제곱이고, 긴 변을 5~10칸으로 나눔", () => {
    const size = cellSize(BOUNDS);
    expect(Number.isInteger(Math.log2(size))).toBe(true);
    // 위도 0.1도는 메르카토르에서 경도 0.1도보다 길어 긴 변이 됩니다.
    const mercatorHeight = 0.1 / Math.cos((37.55 * Math.PI) / 180);
    const cells = mercatorHeight / size;
    expect(cells).toBeGreaterThanOrEqual(5);
    expect(cells).toBeLessThanOrEqual(10);
  });

  it("같은 크기의 범위는 위치가 달라도 칸 크기가 같음 (지도를 옮겨도 묶음이 유지됨)", () => {
    const moved = { west: 127.03, south: 37.5, east: 127.13, north: 37.6 };
    expect(cellSize(moved)).toBe(cellSize(BOUNDS));
  });
});

describe("inBounds", () => {
  it("경계 위의 점도 포함함", () => {
    expect(inBounds({ lat: 37.5, lng: 127.0 }, BOUNDS)).toBe(true);
    expect(inBounds({ lat: 37.61, lng: 127.05 }, BOUNDS)).toBe(false);
  });
});

describe("clusterPlaces", () => {
  it(`좌표가 ${MAX_SPOTS}개 이하이면 묶지 않음`, () => {
    const { places, clusters } = clusterPlaces(spread(MAX_SPOTS), BOUNDS);
    expect(places).toHaveLength(MAX_SPOTS);
    expect(clusters).toEqual([]);
  });

  it("같은 좌표의 여러 곳은 좌표 수가 적어도 한 점짜리 묶음이 됨", () => {
    const sameSpot = [point("a", 37.55, 127.05), point("b", 37.55, 127.05), point("c", 37.56, 127.06)];
    const { places, clusters } = clusterPlaces(sameSpot, BOUNDS);
    expect(places.map((p) => p.id)).toEqual(["c"]);
    expect(clusters).toEqual([
      { lat: 37.55, lng: 127.05, count: 2, bounds: { west: 127.05, south: 37.55, east: 127.05, north: 37.55 } },
    ]);
  });

  it(`좌표가 ${MAX_SPOTS}개를 넘으면 격자로 묶고, 개수 합은 전체와 같음`, () => {
    const all = spread(MAX_SPOTS + 1);
    const { places, clusters } = clusterPlaces(all, BOUNDS);
    expect(places.length + clusters.length).toBeLessThan(MAX_SPOTS);
    expect(places.length + clusters.reduce((sum, c) => sum + c.count, 0)).toBe(all.length);
  });

  it("묶음의 위치는 평균, 범위는 묶인 장소를 모두 포함함", () => {
    const all = spread(MAX_SPOTS + 1);
    const { clusters } = clusterPlaces(all, BOUNDS);
    for (const c of clusters) {
      expect(c.bounds.west).toBeLessThanOrEqual(c.lng);
      expect(c.bounds.east).toBeGreaterThanOrEqual(c.lng);
      expect(c.bounds.south).toBeLessThanOrEqual(c.lat);
      expect(c.bounds.north).toBeGreaterThanOrEqual(c.lat);
    }
  });
});

describe("listPlaces", () => {
  it("이름순으로 정렬함", () => {
    const list = listPlaces([point("1", 37.5, 127, "카페"), point("2", 37.5, 127, "가게"), point("3", 37.5, 127, "나무")]);
    expect(list.map((p) => p.name)).toEqual(["가게", "나무", "카페"]);
  });

  it(`최대 ${MAX_LIST}곳까지만 돌려줌`, () => {
    expect(listPlaces(spread(MAX_LIST + 10))).toHaveLength(MAX_LIST);
  });
});
