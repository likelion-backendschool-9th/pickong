import { describe, expect, it } from "vitest";
import { ALL_CATEGORIES } from "@/entities/place";
import { ADMIN_PLACE_PHOTO_MAX_BYTES, canManageAdminPlace, type AdminPlaceFormValues } from "./models";
import { resolveAdminPlacePhoto, validateAdminPlaceCreate, validateAdminPlaceForm, validateAdminPlacePhoto, validateAdminPlaceUpdate } from "./validation";

const form = (patch: Partial<AdminPlaceFormValues> = {}): AdminPlaceFormValues => ({
  name: "성심당",
  address: "대전 중구 대종로 480",
  category: "CAFE",
  location: { lat: 36.3277, lng: 127.427 },
  businessHours: [],
  hoursExceptions: [],
  ...patch,
});

const fields = (values: AdminPlaceFormValues) => validateAdminPlaceForm(values).issues.map((item) => item.field);

describe("관리 대상", () => {
  it("SYSTEM 공개 장소만 수정·삭제할 수 있습니다", () => {
    expect(canManageAdminPlace("SYSTEM")).toBe(true);
    expect(canManageAdminPlace("SYSTEM", "PUBLIC")).toBe(true);
    expect(canManageAdminPlace("SYSTEM", "PRIVATE")).toBe(false);
    expect(canManageAdminPlace("CUSTOM", "PUBLIC")).toBe(false);
    expect(canManageAdminPlace("AI_EXTRACTED", "PUBLIC")).toBe(false);
    expect(canManageAdminPlace("KAKAO", "PUBLIC")).toBe(false);
    expect(canManageAdminPlace("system", "PUBLIC")).toBe(false);
  });
});

describe("기본 정보 검증", () => {
  it("열 개 카테고리의 정상 입력을 허용합니다", () => {
    expect(ALL_CATEGORIES).toEqual(["CAFE", "RESTAURANT", "PHOTO_SPOT", "SHOP", "EXHIBITION", "STREET", "PARK", "ATTRACTION", "STATION", "ETC"]);
    for (const category of ALL_CATEGORIES) {
      expect(validateAdminPlaceCreate(form({ category })).valid).toBe(true);
    }
  });

  it("경계 좌표를 허용하고 빈 값·공백·없는 타입·잘못된 좌표는 거부합니다", () => {
    expect(validateAdminPlaceForm(form({ location: { lat: -90, lng: -180 } })).valid).toBe(true);
    expect(validateAdminPlaceForm(form({ location: { lat: 90, lng: 180 } })).valid).toBe(true);
    expect(fields(form({ name: "" }))).toEqual(["name"]);
    expect(fields(form({ name: " \u3000 " }))).toEqual(["name"]);
    expect(fields(form({ address: "   " }))).toEqual(["address"]);
    expect(fields(form({ category: "LIBRARY" }))).toEqual(["category"]);
    expect(fields(form({ location: null }))).toEqual(["latitude", "longitude"]);
    expect(fields(form({ location: { lat: Number.NaN, lng: Number.POSITIVE_INFINITY } }))).toEqual(["latitude", "longitude"]);
    expect(fields(form({ location: { lat: 90.1, lng: -180.1 } }))).toEqual(["latitude", "longitude"]);
  });

  it("수정에서 뺀 필드는 유지되므로 검사하지 않고 빈 배열 제거는 허용합니다", () => {
    expect(validateAdminPlaceUpdate({}).valid).toBe(true);
    expect(validateAdminPlaceUpdate({ businessHours: [], hoursExceptions: [] }).valid).toBe(true);
    expect(validateAdminPlaceUpdate({ name: "  " }).issues.map((item) => item.field)).toEqual(["name"]);
    expect(validateAdminPlaceUpdate({ address: "" }).valid).toBe(false);
  });
});

describe("사진 검증", () => {
  const file = (type: string, size: number) => ({ type, size });

  it("허용 형식과 10MB 경계를 구분합니다", () => {
    expect(validateAdminPlacePhoto(file("image/jpeg", 1)).valid).toBe(true);
    expect(validateAdminPlacePhoto(file("image/PNG", ADMIN_PLACE_PHOTO_MAX_BYTES)).valid).toBe(true);
    expect(validateAdminPlacePhoto(file("image/webp", ADMIN_PLACE_PHOTO_MAX_BYTES)).valid).toBe(true);
    expect(validateAdminPlacePhoto(file("image/gif", 1)).issues[0]?.field).toBe("photo");
    expect(validateAdminPlacePhoto(file("image/jpeg", 0)).valid).toBe(false);
    expect(validateAdminPlacePhoto(file("image/jpeg", ADMIN_PLACE_PHOTO_MAX_BYTES + 1)).valid).toBe(false);
  });

  it("유지·교체·삭제를 구분하고 잘못된 파일은 저장 상태로 만들지 않습니다", () => {
    expect(resolveAdminPlacePhoto({ existingImageUrl: "https://cdn.example/a.jpg", file: null, removed: false }).action).toBe("keep");
    expect(resolveAdminPlacePhoto({ existingImageUrl: "https://cdn.example/a.jpg", file: null, removed: true }).action).toBe("delete");
    expect(resolveAdminPlacePhoto({ existingImageUrl: null, file: null, removed: true }).action).toBe("keep");
    const uploaded = resolveAdminPlacePhoto({ existingImageUrl: "https://cdn.example/a.jpg", file: file("image/webp", 10), removed: true });
    expect(uploaded.action).toBe("upload");
    expect(resolveAdminPlacePhoto({ existingImageUrl: null, file: file("image/gif", 10), removed: false }).action).toBe("invalid");
  });
});
