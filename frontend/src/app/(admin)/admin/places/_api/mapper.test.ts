import { describe, expect, it } from "vitest";
import { canManageAdminPlace, type AdminPlaceCreateInput, type AdminPlaceFormValues } from "../_lib/models";
import type { AdminPlaceDetailDto, AdminPlaceSummaryDto } from "./dto";
import { toAdminPlaceCreateRequest, toAdminPlaceDetail, toAdminPlaceFormValues, toAdminPlaceSummary, toAdminPlaceUpdateInput, toAdminPlaceUpdateRequest } from "./mapper";

const summary = (patch: Partial<AdminPlaceSummaryDto> = {}): AdminPlaceSummaryDto => ({
  id: "3f1c0c2e-6b4a-4d2e-9c1a-8b7e6d5c4b3a",
  name: "성심당",
  address: null,
  category: "CAFE",
  sourceType: "SYSTEM",
  imageUrl: null,
  ...patch,
});

const detail = (patch: Partial<AdminPlaceDetailDto> = {}): AdminPlaceDetailDto => ({
  ...summary(),
  visibility: "PUBLIC",
  lat: 36.3277,
  lng: 127.427,
  businessHours: [
    { dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false },
    { dayOfWeek: 0, openTime: 0, closeTime: 0, closed: true },
    { dayOfWeek: 5, openTime: 0, closeTime: 1440, closed: false },
    { dayOfWeek: 6, openTime: 1320, closeTime: 1560, closed: false },
  ],
  hoursExceptions: [
    { exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "휴무" },
    { exceptionDate: "2026-12-24", closed: false, openTime: 600, closeTime: 900, note: null },
  ],
  ...patch,
});

const form = (patch: Partial<AdminPlaceFormValues> = {}): AdminPlaceFormValues => ({
  name: " 성심당 ",
  address: " 대전 중구 ",
  category: "CAFE",
  location: { lat: 36.3277, lng: 127.427 },
  businessHours: [{ dayOfWeek: 1, closed: false, openTime: 540, closeTime: 1320 }],
  hoursExceptions: [{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "  " }],
  ...patch,
});

describe("목록·상세 변환", () => {
  it("주소와 사진이 없는 목록 항목을 그대로 둡니다", () => {
    expect(toAdminPlaceSummary(summary())).toMatchObject({ address: null, imageUrl: null, sourceType: "SYSTEM" });
  });

  it("휴무·24시간·자정 이후·예외를 구분하고 없는 묶음은 빈 배열입니다", () => {
    const place = toAdminPlaceDetail(detail());
    expect(place.businessHours.map((row) => row.closed)).toEqual([false, true, false, false]);
    expect(place.businessHours[2]).toMatchObject({ openTime: 0, closeTime: 1440 });
    expect(place.businessHours[3]).toMatchObject({ openTime: 1320, closeTime: 1560 });
    expect(place.hoursExceptions[0]).toMatchObject({ closed: true, openTime: null, closeTime: null });
    const empty = toAdminPlaceDetail({ ...detail(), businessHours: undefined as unknown as AdminPlaceDetailDto["businessHours"], hoursExceptions: undefined as unknown as AdminPlaceDetailDto["hoursExceptions"] });
    expect(empty.businessHours).toEqual([]);
    expect(empty.hoursExceptions).toEqual([]);
  });

  it("알 수 없는 출처를 SYSTEM으로 바꾸지 않습니다", () => {
    const place = toAdminPlaceDetail(detail({ sourceType: "KAKAO" }));
    expect(place.sourceType).toBe("KAKAO");
    expect(canManageAdminPlace(place.sourceType, place.visibility)).toBe(false);
  });

  it("상세를 폼 초기값으로 바꿀 때 빈 주소는 빈 문자열입니다", () => {
    expect(toAdminPlaceFormValues(toAdminPlaceDetail(detail({ address: null }))).address).toBe("");
  });
});

describe("등록·수정 요청", () => {
  it("등록 요청에 서버가 정하는 필드를 넣지 않습니다", () => {
    const input: AdminPlaceCreateInput = { ...form(), photo: { action: "set", imageUrl: "https://cdn.example/place.jpg" } };
    const body = toAdminPlaceCreateRequest(input);
    expect(body).toMatchObject({
      name: "성심당",
      address: "대전 중구",
      category: "CAFE",
      lat: 36.3277,
      lng: 127.427,
      imageUrl: "https://cdn.example/place.jpg",
    });
    expect(body.hoursExceptions[0]).toMatchObject({ closed: true, openTime: null, closeTime: null, note: null });
    expect(body).not.toHaveProperty("sourceType");
    expect(body).not.toHaveProperty("visibility");
  });

  it("수정에서 빼낸 필드와 사진 유지는 본문에 없고 영업시간 빈 배열은 제거입니다", () => {
    const kept = toAdminPlaceUpdateRequest(toAdminPlaceUpdateInput(form()));
    expect(kept).not.toHaveProperty("imageUrl");
    expect(kept).not.toHaveProperty("sourceType");
    expect(kept).not.toHaveProperty("description");
    expect(toAdminPlaceUpdateRequest({ businessHours: [] })).toEqual({ businessHours: [] });
    expect(toAdminPlaceUpdateRequest({ hoursExceptions: [] })).toEqual({ hoursExceptions: [] });
    expect(toAdminPlaceUpdateRequest({ photo: { action: "delete" } })).toEqual({ imageUrl: null });
  });

  it("휴무 요일은 0분 자리 값으로 보내고 브라우저 주소는 거부합니다", () => {
    const body = toAdminPlaceUpdateRequest({
      businessHours: [{ dayOfWeek: 0, closed: true, openTime: 540, closeTime: 100 }],
    });
    expect(body.businessHours).toEqual([{ dayOfWeek: 0, openTime: 0, closeTime: 0, closed: true }]);
    expect(() => toAdminPlaceUpdateRequest({ photo: { action: "set", imageUrl: "blob:http://local/1" } })).toThrow(/브라우저 임시 주소/);
  });
});
