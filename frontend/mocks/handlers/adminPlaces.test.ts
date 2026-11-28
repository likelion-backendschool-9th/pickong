import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it, vi } from "vitest";
import { adminPlaceApi } from "../../src/app/(admin)/admin/places/_api/adminPlaceApi.ts";
import { ApiError } from "../../src/shared/api/errors.ts";
import { ADMIN_PLACE_IDS } from "../fixtures/adminPlaces.ts";
import { adminPlaceRoutes } from "../handlers/adminPlaces.ts";
import { placeSubmissionRoutes } from "../handlers/placeSubmissions.ts";
import { adminPlaceRecord, listAdminPlaceSummaries, resetAdminPlaces } from "../lib/adminPlaces.ts";
import { db } from "../db.ts";
import { createRouter } from "../router.ts";

const sample = {
  name: "찜콩 테스트 카페",
  address: "서울특별시 중구",
  category: "CAFE",
  lat: 37.5665,
  lng: 126.978,
  businessHours: [{ dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false }],
  hoursExceptions: [] as unknown[],
};

async function start(routes = adminPlaceRoutes) {
  resetAdminPlaces();
  const server = createServer(createRouter(routes));
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", () => resolve()));
  const { port } = server.address() as AddressInfo;
  return { server, base: `http://127.0.0.1:${port}` };
}

async function stop(server: Server) {
  await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
}

async function call(base: string, method: string, path: string, body?: unknown, raw?: string) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: body !== undefined || raw !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await response.text();
  return { status: response.status, text, json: text ? JSON.parse(text) : undefined };
}

describe("관리자 장소 Mock API", () => {
  let server: Server;
  let base: string;

  afterEach(async () => {
    vi.unstubAllGlobals();
    resetAdminPlaces();
    if (server) await stop(server);
  });

  async function up(routes = adminPlaceRoutes) {
    const started = await start(routes);
    server = started.server;
    base = started.base;
  }

  it("공개 공용 장소만 요약 필드로 돌려준다", async () => {
    await up();
    const { status, json } = await call(base, "GET", "/api/v1/admin/places");
    const ids = json.map((row: { id: string }) => row.id);
    expect(status).toBe(200);
    expect(ids).toEqual(expect.arrayContaining([
      ADMIN_PLACE_IDS.systemHours,
      ADMIN_PLACE_IDS.customPublic,
      ADMIN_PLACE_IDS.aiExtracted,
      ADMIN_PLACE_IDS.unknownSource,
    ]));
    expect(ids).not.toContain(ADMIN_PLACE_IDS.customPrivate);
    expect(ids).not.toContain(ADMIN_PLACE_IDS.systemDeleted);
    expect(json[0]).toEqual({
      id: expect.any(String),
      name: expect.any(String),
      address: expect.any(String),
      category: expect.any(String),
      sourceType: expect.any(String),
      imageUrl: expect.anything(),
    });
    expect(json[0]).not.toHaveProperty("businessHours");
    expect(json[0]).not.toHaveProperty("lat");
    expect(json[0]).not.toHaveProperty("visibility");
    expect(json[0]).not.toHaveProperty("description");
    expect(json.find((row: { id: string }) => row.id === ADMIN_PLACE_IDS.unknownSource).sourceType).toBe("KAKAO");
  });

  it("상세는 영업시간과 사진을 포함하고 볼 수 없는 장소는 404다", async () => {
    await up();
    const hours = await call(base, "GET", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemHours}`);
    expect(hours.status).toBe(200);
    expect(hours.json.imageUrl).toBe("/images/photos/cafe.jpg");
    expect(hours.json.businessHours).toEqual([{ dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false }]);
    expect(hours.json.visibility).toBe("PUBLIC");

    const empty = await call(base, "GET", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemEmpty}`);
    expect(empty.json.address).toBeNull();
    expect(empty.json.imageUrl).toBeNull();
    expect(empty.json.businessHours).toEqual([]);
    expect(empty.json.hoursExceptions).toEqual([]);

    const closed = await call(base, "GET", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemClosed}`);
    expect(closed.json.businessHours).toContainEqual({ dayOfWeek: 0, openTime: 0, closeTime: 0, closed: true });
    expect(closed.json.businessHours.some((row: { dayOfWeek: number }) => row.dayOfWeek === 6)).toBe(false);
    expect(closed.json.hoursExceptions[0]).toMatchObject({ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "임시 휴무" });

    const allDay = await call(base, "GET", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemAllDay}`);
    expect(allDay.json.businessHours).toEqual([{ dayOfWeek: 2, openTime: 0, closeTime: 1440, closed: false }]);
    const overnight = await call(base, "GET", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemOvernight}`);
    expect(overnight.json.businessHours).toEqual([{ dayOfWeek: 5, openTime: 1080, closeTime: 1560, closed: false }]);

    for (const id of ["missing-id", ADMIN_PLACE_IDS.customPrivate, ADMIN_PLACE_IDS.systemDeleted]) {
      const missing = await call(base, "GET", `/api/v1/admin/places/${id}`);
      expect(missing.status).toBe(404);
      expect(missing.json).toEqual({ code: "PLACE_NOT_FOUND", message: "장소를 찾을 수 없어요." });
    }
  });

  it("등록하면 SYSTEM 공개 장소가 되고 잘못된 요청은 저장하지 않는다", async () => {
    await up();
    const before = (await call(base, "GET", "/api/v1/admin/places")).json.length;
    const created = await call(base, "POST", "/api/v1/admin/places", {
      ...sample,
      sourceType: "CUSTOM",
      visibility: "PRIVATE",
      hoursExceptions: [{ exceptionDate: "2026-01-02", closed: false, openTime: 600, closeTime: 720, note: "메모" }],
    });
    expect(created.status).toBe(201);
    expect(created.json.sourceType).toBe("SYSTEM");
    expect(created.json.visibility).toBe("PUBLIC");
    expect(created.json.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(created.json.businessHours).toEqual(sample.businessHours);
    expect(created.json.hoursExceptions[0].note).toBe("메모");
    expect(created.json.imageUrl).toBeNull();

    const listed = await call(base, "GET", "/api/v1/admin/places");
    expect(listed.json.map((row: { id: string }) => row.id)).toContain(created.json.id);
    const detail = await call(base, "GET", `/api/v1/admin/places/${created.json.id}`);
    expect(detail.json.name).toBe(sample.name);

    const again = await call(base, "POST", "/api/v1/admin/places", sample);
    expect(again.status).toBe(201);
    expect(again.json.id).not.toBe(created.json.id);

    const rejects = [
      { ...sample, name: "  " },
      { ...sample, address: "" },
      { ...sample, category: "FOOD" },
      { ...sample, lat: 91 },
      { ...sample, businessHours: [{ dayOfWeek: 1, openTime: 1320, closeTime: 540, closed: false }] },
      { ...sample, hoursExceptions: [{ exceptionDate: "2026-02-31", closed: true, openTime: null, closeTime: null, note: null }] },
      { ...sample, imageUrl: "blob:http://localhost/photo" },
      { ...sample, imageUrl: "data:image/png;base64,aaaa" },
    ];
    for (const body of rejects) {
      const failed = await call(base, "POST", "/api/v1/admin/places", body);
      expect(failed.status).toBe(400);
      expect(failed.json.code).toBe("INVALID_INPUT");
    }
    const broken = await call(base, "POST", "/api/v1/admin/places", undefined, "{");
    expect(broken.status).toBe(400);
    const after = (await call(base, "GET", "/api/v1/admin/places")).json.length;
    expect(after).toBe(before + 2);
  });

  it("전달한 필드만 바꾸고 실패하면 기존 값을 유지한다", async () => {
    await up();
    const id = ADMIN_PLACE_IDS.systemHours;
    const renamed = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "이름만 변경", sourceType: "CUSTOM", visibility: "PRIVATE" });
    expect(renamed.status).toBe(200);
    expect(renamed.json.name).toBe("이름만 변경");
    expect(renamed.json.sourceType).toBe("SYSTEM");
    expect(renamed.json.visibility).toBe("PUBLIC");
    expect(renamed.json.businessHours).toHaveLength(1);
    expect(renamed.json.imageUrl).toBe("/images/photos/cafe.jpg");
    const kept = adminPlaceRecord(id);
    expect(kept?.description).toBe("관리자 폼에 없는 설명");
    expect(kept?.phone).toBe("02-000-0000");
    expect(kept?.region).toBe("서울");

    const moved = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { lng: 127.1 });
    expect(moved.json.lng).toBe(127.1);
    expect(moved.json.lat).toBe(37.5446);
    const addressed = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { address: "새 주소", category: "PARK" });
    expect(addressed.json.address).toBe("새 주소");
    expect(addressed.json.category).toBe("PARK");

    const listed = await call(base, "GET", "/api/v1/admin/places");
    expect(listed.json.find((row: { id: string }) => row.id === id).name).toBe("이름만 변경");

    const replaced = await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      businessHours: [{ dayOfWeek: 3, openTime: 0, closeTime: 0, closed: true }],
      hoursExceptions: [{ exceptionDate: "2026-03-01", closed: false, openTime: 540, closeTime: 600, note: "" }],
      imageUrl: "/images/photos/next.jpg",
    });
    expect(replaced.json.businessHours).toEqual([{ dayOfWeek: 3, openTime: 0, closeTime: 0, closed: true }]);
    expect(replaced.json.hoursExceptions).toEqual([{ exceptionDate: "2026-03-01", closed: false, openTime: 540, closeTime: 600, note: null }]);
    expect(replaced.json.imageUrl).toBe("/images/photos/next.jpg");

    const cleared = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { businessHours: [], hoursExceptions: [], imageUrl: null });
    expect(cleared.json.businessHours).toEqual([]);
    expect(cleared.json.hoursExceptions).toEqual([]);
    expect(cleared.json.imageUrl).toBeNull();

    const beforeFail = await call(base, "GET", `/api/v1/admin/places/${id}`);
    const failed = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "실패하면 안 바뀜", category: "NOPE", lat: 200 });
    expect(failed.status).toBe(400);
    const afterFail = await call(base, "GET", `/api/v1/admin/places/${id}`);
    expect(afterFail.json).toEqual(beforeFail.json);

    const emptyAddress = await call(base, "PATCH", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemEmpty}`, { name: "주소 없이 이름만" });
    expect(emptyAddress.status).toBe(200);
    expect(emptyAddress.json.address).toBeNull();

    const normalized = await call(base, "PATCH", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemAllDay}`, {
      businessHours: [{ dayOfWeek: 2, openTime: 999, closeTime: 1, closed: true }],
    });
    expect(normalized.json.businessHours).toEqual([{ dayOfWeek: 2, openTime: 0, closeTime: 0, closed: true }]);
  });

  it("SYSTEM이 아닌 공용 장소는 409, 조회할 수 없는 장소는 404다", async () => {
    await up();
    for (const id of [ADMIN_PLACE_IDS.customPublic, ADMIN_PLACE_IDS.aiExtracted, ADMIN_PLACE_IDS.unknownSource]) {
      const updated = await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "수정 불가" });
      const removed = await call(base, "DELETE", `/api/v1/admin/places/${id}`);
      expect(updated.status).toBe(409);
      expect(updated.json.code).toBe("PLACE_NOT_MANAGEABLE");
      expect(removed.status).toBe(409);
      expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).json.name).not.toBe("수정 불가");
    }
    for (const id of [ADMIN_PLACE_IDS.customPrivate, ADMIN_PLACE_IDS.systemDeleted, "00000000-0000-4000-8000-000000000000"]) {
      expect((await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "없음" })).status).toBe(404);
      expect((await call(base, "DELETE", `/api/v1/admin/places/${id}`)).status).toBe(404);
    }
  });

  it("삭제는 204이고 목록과 상세에서 빠지며 다른 저장소는 그대로다", async () => {
    await up([...adminPlaceRoutes, ...placeSubmissionRoutes]);
    const placesBefore = db.places.map((place) => place.id);
    const savedBefore = db.savedPlaces.length;
    const submissionsBefore = (await call(base, "GET", "/api/v1/admin/place-submissions")).json.length;
    const id = ADMIN_PLACE_IDS.systemOvernight;
    const removed = await call(base, "DELETE", `/api/v1/admin/places/${id}`);
    expect(removed.status).toBe(204);
    expect(removed.text).toBe("");
    expect((await call(base, "GET", "/api/v1/admin/places")).json.map((row: { id: string }) => row.id)).not.toContain(id);
    expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).status).toBe(404);
    expect((await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "삭제 후 수정" })).status).toBe(404);
    expect((await call(base, "DELETE", `/api/v1/admin/places/${id}`)).status).toBe(404);
    expect(adminPlaceRecord(id)?.name).toBe("찜콩 야간");
    expect(db.places.map((place) => place.id)).toEqual(placesBefore);
    expect(db.savedPlaces).toHaveLength(savedBefore);
    expect((await call(base, "GET", "/api/v1/admin/place-submissions")).json).toHaveLength(submissionsBefore);
  });

  it("같은 요일과 같은 예외 날짜, 잘못된 시간 범위는 거부한다", async () => {
    await up();
    const id = ADMIN_PLACE_IDS.systemEmpty;
    const duplicateDay = await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      businessHours: [
        { dayOfWeek: 1, openTime: 540, closeTime: 600, closed: false },
        { dayOfWeek: 1, openTime: 700, closeTime: 800, closed: false },
      ],
    });
    const tooLong = await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      businessHours: [{ dayOfWeek: 1, openTime: 0, closeTime: 1441, closed: false }],
    });
    const duplicateDate = await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      hoursExceptions: [
        { exceptionDate: "2026-04-01", closed: true, openTime: null, closeTime: null, note: null },
        { exceptionDate: "2026-04-01", closed: true, openTime: null, closeTime: null, note: null },
      ],
    });
    const timedHoliday = await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      hoursExceptions: [{ exceptionDate: "2026-04-02", closed: true, openTime: 540, closeTime: 600, note: null }],
    });
    expect([duplicateDay, tooLong, duplicateDate, timedHoliday].map((row) => row.status)).toEqual([400, 400, 400, 400]);
    expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).json.businessHours).toEqual([]);
  });

  it("등록부터 삭제까지 다음 조회에 반영된다", async () => {
    await up();
    const initial = (await call(base, "GET", "/api/v1/admin/places")).json.length;
    const created = await call(base, "POST", "/api/v1/admin/places", sample);
    const id = created.json.id;
    expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).json.category).toBe("CAFE");
    await call(base, "PATCH", `/api/v1/admin/places/${id}`, { name: "수정된 카페", category: "SHOP" });
    await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      businessHours: [{ dayOfWeek: 4, openTime: 0, closeTime: 1440, closed: false }],
    });
    await call(base, "PATCH", `/api/v1/admin/places/${id}`, {
      hoursExceptions: [{ exceptionDate: "2026-06-06", closed: true, openTime: null, closeTime: null, note: "휴무" }],
    });
    await call(base, "PATCH", `/api/v1/admin/places/${id}`, { imageUrl: "/images/photos/cafe.jpg" });
    const edited = await call(base, "GET", `/api/v1/admin/places/${id}`);
    expect(edited.json).toMatchObject({
      name: "수정된 카페",
      category: "SHOP",
      imageUrl: "/images/photos/cafe.jpg",
      businessHours: [{ dayOfWeek: 4, openTime: 0, closeTime: 1440, closed: false }],
    });
    expect(edited.json.hoursExceptions[0].note).toBe("휴무");
    await call(base, "PATCH", `/api/v1/admin/places/${id}`, { imageUrl: null });
    expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).json.imageUrl).toBeNull();
    expect((await call(base, "DELETE", `/api/v1/admin/places/${id}`)).status).toBe(204);
    const after = await call(base, "GET", "/api/v1/admin/places");
    expect(after.json).toHaveLength(initial);
    expect((await call(base, "GET", `/api/v1/admin/places/${id}`)).status).toBe(404);
  });

  it("adminPlaceApi가 같은 Mock 핸들러를 사용한다", async () => {
    await up();
    const native = globalThis.fetch.bind(globalThis);
    vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      return native(url.startsWith("/api/") ? `${base}${url}` : input, init);
    });
    const listed = await adminPlaceApi.list();
    expect(listed.some((place) => place.id === ADMIN_PLACE_IDS.systemHours && place.imageUrl === "/images/photos/cafe.jpg")).toBe(true);
    const created = await adminPlaceApi.create({
      name: " API 카페 ",
      address: "서울",
      category: "CAFE",
      location: { lat: 37.5, lng: 127 },
      businessHours: [{ dayOfWeek: 1, closed: false, openTime: 540, closeTime: 1320 }],
      hoursExceptions: [],
    });
    expect(created.sourceType).toBe("SYSTEM");
    expect(created.name).toBe("API 카페");
    const updated = await adminPlaceApi.update(created.id, { photo: { action: "set", imageUrl: "/images/photos/cafe.jpg" } });
    expect(updated.imageUrl).toBe("/images/photos/cafe.jpg");
    expect(updated.businessHours).toHaveLength(1);
    await adminPlaceApi.remove(created.id);
    await expect(adminPlaceApi.detail(created.id)).rejects.toBeInstanceOf(ApiError);
    await expect(adminPlaceApi.detail(created.id)).rejects.toMatchObject({ status: 404, code: "PLACE_NOT_FOUND" });
  });

  it("테스트를 다시 시작하면 fixture로 돌아간다", async () => {
    await up();
    await call(base, "DELETE", `/api/v1/admin/places/${ADMIN_PLACE_IDS.systemHours}`);
    expect(listAdminPlaceSummaries().some((row) => row.id === ADMIN_PLACE_IDS.systemHours)).toBe(false);
    resetAdminPlaces();
    expect(listAdminPlaceSummaries().some((row) => row.id === ADMIN_PLACE_IDS.systemHours)).toBe(true);
    const original = adminPlaceRecord(ADMIN_PLACE_IDS.systemHours);
    original?.businessHours.push({ dayOfWeek: 6, openTime: 0, closeTime: 1, closed: false });
    expect(adminPlaceRecord(ADMIN_PLACE_IDS.systemHours)?.businessHours).toHaveLength(1);
  });
});
