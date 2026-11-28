import { describe, expect, it } from "vitest";
import type { AdminPlaceSummary } from "./models";
import {
  adminPlaceCategoryText,
  adminPlaceImageState,
  adminPlaceDetailHref,
  adminPlaceListHref,
  adminPlaceReturnQuery,
  adminPlaceSearchParams,
  adminPlaceSourceText,
  filterAdminPlaces,
  hasAdminPlaceFilter,
  parseAdminPlaceQuery,
  type AdminPlaceListQuery,
} from "./filterAdminPlaces";

function place(patch: Partial<AdminPlaceSummary> & Pick<AdminPlaceSummary, "id" | "name" | "sourceType">): AdminPlaceSummary {
  return { address: "서울", category: "CAFE", imageUrl: null, ...patch };
}

const items = [
  place({ id: "a", name: "찜콩 카페", address: "서울 성동구 성수동", category: "CAFE", sourceType: "SYSTEM" }),
  place({ id: "b", name: "Cafe Spot", address: "Busan", category: "SHOP", sourceType: "CUSTOM" }),
  place({ id: "c", name: "추출된 장소", address: "서울 용산구", category: "EXHIBITION", sourceType: "AI_EXTRACTED" }),
  place({ id: "e", name: "주소 없는 장소", address: null, category: "ETC", sourceType: "SYSTEM" }),
  place({ id: "j", name: "출처를 알 수 없는 장소", address: "서울 중구", category: "PHOTO_SPOT", sourceType: "KAKAO" }),
  place({ id: "u", name: "분류 없음", address: "대전", category: null, sourceType: "SYSTEM" }),
  place({ id: "f", name: "알 수 없는 분류", address: "부산", category: "FOOD", sourceType: "CUSTOM" }),
];

const query = (patch: Partial<AdminPlaceListQuery> = {}): AdminPlaceListQuery => ({ q: "", category: "ALL", source: "ALL", ...patch });

describe("관리자 장소 목록 필터", () => {
  it("빈 검색어는 순서를 유지한 전체 목록을 돌려주고 원본을 바꾸지 않는다", () => {
    const before = items.map((item) => item.name);
    const result = filterAdminPlaces(items, query());
    expect(result.map((item) => item.id)).toEqual(items.map((item) => item.id));
    expect(result[0]).toBe(items[0]);
    expect(items.map((item) => item.name)).toEqual(before);
  });

  it("이름과 주소를 부분 검색하고 공백과 영문 대소문자를 무시한다", () => {
    expect(filterAdminPlaces(items, query({ q: "  찜콩  " })).map((item) => item.id)).toEqual(["a"]);
    expect(filterAdminPlaces(items, query({ q: "성수동" })).map((item) => item.id)).toEqual(["a"]);
    expect(filterAdminPlaces(items, query({ q: "cafe" })).map((item) => item.id)).toEqual(["b"]);
    expect(filterAdminPlaces(items, query({ q: "주소 없는" })).map((item) => item.id)).toEqual(["e"]);
    expect(filterAdminPlaces(items, query({ q: "없는주소" }))).toEqual([]);
  });

  it("카테고리와 출처를 함께 거르고 알 수 없는 값은 알려진 값으로 바꾸지 않는다", () => {
    expect(filterAdminPlaces(items, query({ category: "CAFE" })).map((item) => item.id)).toEqual(["a"]);
    expect(filterAdminPlaces(items, query({ category: "STATION" }))).toEqual([]);
    expect(filterAdminPlaces(items, query({ source: "SYSTEM" })).map((item) => item.id)).toEqual(["a", "e", "u"]);
    expect(filterAdminPlaces(items, query({ source: "CUSTOM" })).map((item) => item.id)).toEqual(["b", "f"]);
    expect(filterAdminPlaces(items, query({ source: "AI_EXTRACTED" })).map((item) => item.id)).toEqual(["c"]);
    expect(filterAdminPlaces(items, query({ source: "OTHER" })).map((item) => item.id)).toEqual(["j"]);
    expect(filterAdminPlaces(items, query({ q: "서울", category: "CAFE", source: "SYSTEM" })).map((item) => item.id)).toEqual(["a"]);
    expect(filterAdminPlaces(items, query({ q: "찜콩", category: "PARK", source: "SYSTEM" }))).toEqual([]);
    expect(adminPlaceCategoryText("FOOD")).toBe("FOOD");
    expect(adminPlaceCategoryText(null)).toBe("카테고리 없음");
    expect(adminPlaceCategoryText("CAFE")).toBe("카페");
    expect(adminPlaceSourceText("KAKAO")).toBe("기타 출처");
    expect(adminPlaceSourceText("SYSTEM")).toBe("시스템");
  });

  it("주소의 검색 조건을 기본값 없이 해석하고 잘못된 값은 전체로 본다", () => {
    expect(parseAdminPlaceQuery(new URLSearchParams())).toEqual(query());
    expect(parseAdminPlaceQuery(new URLSearchParams("q=성수&category=PARK&source=OTHER"))).toEqual(query({ q: "성수", category: "PARK", source: "OTHER" }));
    expect(parseAdminPlaceQuery(new URLSearchParams("category=FOOD&source=KAKAO"))).toEqual(query());
    expect(adminPlaceSearchParams(query())).toBe("");
    expect(adminPlaceSearchParams(query({ q: " 성수 ", category: "CAFE", source: "SYSTEM" }))).toBe("q=%EC%84%B1%EC%88%98&category=CAFE&source=SYSTEM");
    expect(hasAdminPlaceFilter(query())).toBe(false);
    expect(hasAdminPlaceFilter(query({ q: " " }))).toBe(false);
    expect(hasAdminPlaceFilter(query({ source: "OTHER" }))).toBe(true);
  });

  it("사진 주소가 없거나 읽기에 실패하면 대체 표시로 구분한다", () => {
    expect(adminPlaceImageState(null, false)).toBe("empty");
    expect(adminPlaceImageState("  ", false)).toBe("empty");
    expect(adminPlaceImageState("/images/photos/cafe.jpg", false)).toBe("show");
    expect(adminPlaceImageState("/images/photos/cafe.jpg", true)).toBe("failed");
  });

  it("목록으로 돌아갈 때 검색 조건만 남긴다", () => {
    expect(adminPlaceReturnQuery("https://evil.example")).toBe("");
    expect(adminPlaceReturnQuery("q=성수&category=CAFE&source=SYSTEM&next=https://evil.example")).toBe("q=%EC%84%B1%EC%88%98&category=CAFE&source=SYSTEM");
    expect(adminPlaceDetailHref("a", "q=성수", true)).toContain("/admin/places/a?");
    expect(adminPlaceListHref(new URLSearchParams("list=q%3D%EC%84%B1%EC%88%98"))).toBe("/admin/places?q=%EC%84%B1%EC%88%98");
  });
});
