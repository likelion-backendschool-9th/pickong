import { afterEach, describe, expect, it, vi } from "vitest";
import type { AdminPlaceCreateInput } from "../_lib/models";
import { adminPlaceApi } from "./adminPlaceApi";

afterEach(() => vi.unstubAllGlobals());

const detail = {
  id: "3f1c0c2e-6b4a-4d2e-9c1a-8b7e6d5c4b3a",
  name: "성심당",
  address: "대전 중구",
  category: "CAFE",
  sourceType: "SYSTEM",
  visibility: "PUBLIC",
  lat: 36.3,
  lng: 127.4,
  imageUrl: null,
  businessHours: [],
  hoursExceptions: [],
};

const input: AdminPlaceCreateInput = {
  name: "성심당",
  address: "대전 중구",
  category: "CAFE",
  location: { lat: 36.3, lng: 127.4 },
  businessHours: [],
  hoursExceptions: [],
};

function mockResponse(status: number, body?: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("document", { cookie: "XSRF-TOKEN=csrf-value" });
  return fetchMock;
}

describe("관리자 장소 API", () => {
  it("목록과 상세는 UUID 경로로 조회합니다", async () => {
    const fetchMock = mockResponse(200, [detail]);
    expect(await adminPlaceApi.list()).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/places", expect.objectContaining({ method: "GET", credentials: "include" }));
    mockResponse(200, detail);
    expect((await adminPlaceApi.detail(detail.id)).id).toBe(detail.id);
  });

  it("등록은 SYSTEM과 공개 범위를 본문에 넣지 않고 201 상세를 돌려줍니다", async () => {
    const fetchMock = mockResponse(201, detail);
    expect((await adminPlaceApi.create(input)).sourceType).toBe("SYSTEM");
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).not.toHaveProperty("sourceType");
    expect(init.headers).toMatchObject({ "X-XSRF-TOKEN": "csrf-value" });
  });

  it("수정 PATCH와 삭제 204를 회원 장소 경로와 섞지 않습니다", async () => {
    const fetchMock = mockResponse(200, detail);
    await adminPlaceApi.update(detail.id, { photo: { action: "delete" } });
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/admin/places/${detail.id}`, expect.objectContaining({
      method: "PATCH",
      body: JSON.stringify({ imageUrl: null }),
    }));
    mockResponse(204);
    await expect(adminPlaceApi.remove(detail.id)).resolves.toBeUndefined();
  });

  it("오류 본문을 성공으로 바꾸지 않습니다", async () => {
    mockResponse(409, { code: "PLACE_NOT_MANAGEABLE", message: "SYSTEM 장소만 수정할 수 있어요." });
    await expect(adminPlaceApi.update(detail.id, { name: "다른 이름" })).rejects.toMatchObject({
      status: 409,
      code: "PLACE_NOT_MANAGEABLE",
    });
  });
});
