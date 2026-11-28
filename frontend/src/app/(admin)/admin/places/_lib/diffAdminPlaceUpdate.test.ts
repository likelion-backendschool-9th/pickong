import { describe, expect, it } from "vitest";
import { toAdminPlaceUpdateRequest } from "../_api/mapper";
import { diffAdminPlaceUpdate, hasAdminPlaceChanges } from "./diffAdminPlaceUpdate";
import type { AdminPlaceFormValues } from "./models";

const base = (): AdminPlaceFormValues => ({
  name: "찜콩 카페",
  address: "서울 성동구",
  category: "CAFE",
  location: { lat: 37.5445, lng: 127.056 },
  businessHours: [
    { dayOfWeek: 0, closed: true, openTime: 0, closeTime: 0 },
    { dayOfWeek: 1, closed: false, openTime: 540, closeTime: 1320 },
  ],
  hoursExceptions: [{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "휴무" }],
});

describe("관리자 장소 수정 비교", () => {
  it("바꾼 필드만 고르고 순서만 다른 배열은 그대로 둔다", () => {
    const current = base();
    current.businessHours = [...current.businessHours].reverse();
    current.hoursExceptions = [...current.hoursExceptions];
    expect(hasAdminPlaceChanges(diffAdminPlaceUpdate(base(), current))).toBe(false);

    const renamed = { ...base(), name: "  찜콩 카페 수정  " };
    const patch = diffAdminPlaceUpdate(base(), renamed);
    expect(patch).toEqual({ name: "  찜콩 카페 수정  " });
    const body = toAdminPlaceUpdateRequest(patch);
    expect(body).toEqual({ name: "찜콩 카페 수정" });
    expect(body).not.toHaveProperty("sourceType");
    expect(body).not.toHaveProperty("visibility");
    expect(body).not.toHaveProperty("id");
  });

  it("주소, 좌표, 영업시간 제거, 예외 제거, 사진 삭제를 구분한다", () => {
    const current = { ...base(), address: "서울 성동구 변경", location: { lat: 37.545, lng: 127.057 }, businessHours: [], hoursExceptions: [] };
    const patch = diffAdminPlaceUpdate(base(), current, { action: "delete" });
    expect(toAdminPlaceUpdateRequest(patch)).toEqual({
      address: "서울 성동구 변경",
      lat: 37.545,
      lng: 127.057,
      businessHours: [],
      hoursExceptions: [],
      imageUrl: null,
    });
  });

  it("주소가 없던 장소는 빈 주소를 보내지 않고, 있던 주소를 지우는 값은 남긴다", () => {
    const empty = { ...base(), address: "" };
    expect(diffAdminPlaceUpdate(empty, { ...empty, name: "새 이름" })).toEqual({ name: "새 이름" });
    expect(diffAdminPlaceUpdate(base(), { ...base(), address: "   " })).toMatchObject({ address: "   " });
  });

  it("공백만 다르거나 원래 값으로 되돌리면 요청할 내용이 없다", () => {
    expect(hasAdminPlaceChanges(diffAdminPlaceUpdate(base(), { ...base(), name: " 찜콩 카페 " }))).toBe(false);
    expect(hasAdminPlaceChanges(diffAdminPlaceUpdate(base(), base(), { action: "keep" }))).toBe(false);
    expect(hasAdminPlaceChanges(diffAdminPlaceUpdate(base(), { ...base(), category: "FOOD" }))).toBe(true);
  });
});
