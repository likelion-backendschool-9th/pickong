import { describe, expect, it } from "vitest";
import { matchRank, searchPlaces } from "./placeSearch.ts";

const place = (name: string, region = "성수동", address = "서울특별시 성동구") => ({ name, region, address });

describe("matchRank", () => {
  it("이름 일치 > 이름 시작 > 이름 포함 > 동네 > 주소 순", () => {
    expect(matchRank(place("카페"), "카페")).toBe(0);
    expect(matchRank(place("카페 온유"), "카페")).toBe(1);
    expect(matchRank(place("성수 카페"), "카페")).toBe(2);
    expect(matchRank(place("온유"), "성수")).toBe(3);
    expect(matchRank(place("온유", "서교동", "서울특별시 성동구"), "성동")).toBe(4);
  });

  it("공백과 대소문자를 무시하고, 일치하지 않으면 null", () => {
    expect(matchRank(place("Cafe Onyu"), "cafeon")).toBe(1);
    expect(matchRank(place("온유"), "명동")).toBeNull();
    expect(matchRank(place("온유"), "  ")).toBeNull();
  });
});

describe("searchPlaces", () => {
  it("가까운 순서로 limit곳까지, 순위가 같으면 원래 순서를 유지함", () => {
    const places = [place("성수 카페"), place("카페 B"), place("카페"), place("카페 A"), place("빵집")];
    expect(searchPlaces(places, "카페", 3).map((p) => p.name)).toEqual(["카페", "카페 B", "카페 A"]);
  });
});
