import { describe, expect, it } from "vitest";
import { parseCsvLine, storeCategory, storeRegion, toStorePlace, type StoreCsvRow } from "./storePlace.ts";

const row = (overrides: Partial<StoreCsvRow> = {}): StoreCsvRow => ({
  상가업소번호: "MA0001",
  상호명: "매머드익스프레스",
  지점명: "",
  상권업종소분류코드: "I21201",
  상권업종소분류명: "카페",
  행정동명: "성수2가3동",
  지번주소: "서울특별시 성동구 성수동2가 1",
  도로명주소: "서울특별시 성동구 연무장길 1",
  경도: "127.0559123456",
  위도: "37.5446123456",
  ...overrides,
});

describe("storeCategory", () => {
  it("변환표의 소분류코드를 카테고리로 바꿈", () => {
    expect(storeCategory("I21201")).toBe("CAFE");
    expect(storeCategory("I21001")).toBe("CAFE");
    expect(storeCategory("I20101")).toBe("RESTAURANT");
    expect(storeCategory("I21104")).toBe("RESTAURANT");
    expect(storeCategory("G21901")).toBe("SHOP");
    expect(storeCategory("G21801")).toBe("EXHIBITION");
  });

  it("넣지 않는 업종은 null (편의점 · 약국 · 유흥 주점 · 구내식당 · 부동산)", () => {
    for (const code of ["G20405", "G21501", "I21101", "I20701", "L10203"]) {
      expect(storeCategory(code)).toBeNull();
    }
  });
});

describe("storeRegion", () => {
  it.each([
    ["성수2가3동", "성수동"],
    ["성수1가1동", "성수동"],
    ["역삼1동", "역삼동"],
    ["잠실본동", "잠실동"],
    ["명동", "명동"],
    ["서교동", "서교동"],
  ])("%s → %s", (input, expected) => {
    expect(storeRegion(input)).toBe(expected);
  });
});

describe("toStorePlace", () => {
  it("필요한 값만 골라 한 행으로 만듦", () => {
    expect(toStorePlace(row())).toEqual([
      "s-MA0001",
      "매머드익스프레스",
      "서울특별시 성동구 연무장길 1",
      "성수동",
      37.544612,
      127.055912,
      "CAFE",
      "카페",
    ]);
  });

  it("지점명이 있으면 이름 뒤에 붙임", () => {
    expect(toStorePlace(row({ 지점명: "성수점" }))?.[1]).toBe("매머드익스프레스 성수점");
  });

  it("도로명주소가 없으면 지번주소를 씀", () => {
    expect(toStorePlace(row({ 도로명주소: "" }))?.[2]).toBe("서울특별시 성동구 성수동2가 1");
  });

  it("넣지 않는 업종이거나 좌표가 없으면 null", () => {
    expect(toStorePlace(row({ 상권업종소분류코드: "G20405" }))).toBeNull();
    expect(toStorePlace(row({ 위도: "" }))).toBeNull();
    expect(toStorePlace(row({ 경도: "abc" }))).toBeNull();
  });
});

describe("parseCsvLine", () => {
  it("쉼표로 나눔 (빈 값 포함)", () => {
    expect(parseCsvLine("a,,c")).toEqual(["a", "", "c"]);
  });

  it("따옴표 안의 쉼표와 이스케이프한 따옴표를 처리함", () => {
    expect(parseCsvLine('"a,b","say ""hi""",c')).toEqual(["a,b", 'say "hi"', "c"]);
  });
});
