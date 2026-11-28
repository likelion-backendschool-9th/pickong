import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { toAdminPlaceCreateRequest } from "@/app/(admin)/admin/places/_api/mapper";
import { db } from "../db.ts";
import { adminPlaceRoutes } from "./adminPlaces.ts";
import { handleAdminPlaceMedia } from "./adminPlaceMedia.ts";
import { resetAdminPlaceMedia } from "../lib/adminPlaceMedia.ts";
import { resetAdminPlaces } from "../lib/adminPlaces.ts";
import { createRouter } from "../router.ts";

const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0x00, 0x11]);
const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]);
const webp = Buffer.concat([Buffer.from("RIFF"), Buffer.from([0, 0, 0, 0]), Buffer.from("WEBP"), Buffer.from([1])]);

describe("관리자 장소 Mock 사진", () => {
  let server: Server;
  let base: string;

  afterEach(async () => {
    resetAdminPlaceMedia();
    resetAdminPlaces();
    if (server) await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  });

  async function up() {
    resetAdminPlaceMedia();
    resetAdminPlaces();
    const router = createRouter(adminPlaceRoutes);
    server = createServer(async (req, res) => {
      if (await handleAdminPlaceMedia(req, res)) return;
      return router(req, res);
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", () => resolve()));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  }

  async function upload(body: Buffer, type: string, extra?: HeadersInit) {
    const response = await fetch(`${base}/api/v1/admin/places/mock-media`, {
      method: "POST",
      headers: { "Content-Type": type, ...extra },
      body: new Uint8Array(body),
    });
    const text = await response.text();
    return { status: response.status, json: text ? JSON.parse(text) : undefined };
  }

  it("올린 바이트를 같은 주소로 다시 받고 장소 등록에 쓸 수 있다", async () => {
    await up();
    const placesBefore = db.places.length;
    const savedBefore = db.savedPlaces.length;
    const pngUpload = await upload(png, "image/png");
    const webpUpload = await upload(webp, "image/webp");
    const jpegUpload = await upload(jpeg, "image/jpeg");
    expect(pngUpload.status).toBe(201);
    expect(webpUpload.status).toBe(201);
    expect(jpegUpload.json.imageUrl).toMatch(/^\/api\/v1\/admin\/places\/mock-media\/[0-9a-f-]{36}$/);
    expect(jpegUpload.json.imageUrl).not.toBe(pngUpload.json.imageUrl);

    const image = await fetch(`${base}${jpegUpload.json.imageUrl}`);
    expect(image.headers.get("content-type")).toBe("image/jpeg");
    expect(Buffer.from(await image.arrayBuffer())).toEqual(jpeg);

    const created = await fetch(`${base}/api/v1/admin/places`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: "사진 있는 장소",
        address: "서울",
        category: "CAFE",
        lat: 37.5,
        lng: 127,
        businessHours: [{ dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false }],
        hoursExceptions: [],
        imageUrl: jpegUpload.json.imageUrl,
      }),
    });
    expect(created.status).toBe(201);
    const detail = await created.json();
    expect(detail.sourceType).toBe("SYSTEM");
    expect(detail.visibility).toBe("PUBLIC");
    expect(detail.imageUrl).toBe(jpegUpload.json.imageUrl);
    const listed = await (await fetch(`${base}/api/v1/admin/places`)).json();
    expect(listed.some((place: { id: string; imageUrl: string }) => place.id === detail.id && place.imageUrl === jpegUpload.json.imageUrl)).toBe(true);
    const existing = await fetch(`${base}/api/v1/admin/places/11111111-1111-4111-8111-111111111111`);
    expect(existing.status).toBe(200);
    expect((await existing.json()).name).toBe("찜콩 카페");
    expect(db.places).toHaveLength(placesBefore);
    expect(db.savedPlaces).toHaveLength(savedBefore);
  });

  it("영업시간, 예외, 사진을 한 등록 요청으로 보내고 출처는 서버가 정한다", async () => {
    await up();
    const uploaded = await upload(jpeg, "image/jpeg");
    const body = toAdminPlaceCreateRequest({
      name: "  관리자 등록 테스트 카페  ",
      address: " 서울 성동구 성수동 ",
      category: "CAFE",
      location: { lat: 37.5445, lng: 127.056 },
      businessHours: [
        { dayOfWeek: 1, closed: false, openTime: 540, closeTime: 1320 },
        { dayOfWeek: 0, closed: true, openTime: 0, closeTime: 0 },
        { dayOfWeek: 2, closed: false, openTime: 0, closeTime: 1440 },
        { dayOfWeek: 5, closed: false, openTime: 1080, closeTime: 1560 },
      ],
      hoursExceptions: [{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "크리스마스 휴무" }],
      photo: { action: "set", imageUrl: uploaded.json.imageUrl },
    });
    expect(body).not.toHaveProperty("sourceType");
    expect(body).not.toHaveProperty("visibility");
    expect(JSON.stringify(body)).not.toContain("blob:");
    expect(JSON.stringify(body)).not.toContain("data:");
    const before = await (await fetch(`${base}/api/v1/admin/places`)).json();
    const created = await fetch(`${base}/api/v1/admin/places`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    expect(created.status).toBe(201);
    const detail = await created.json();
    expect(detail.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(detail.name).toBe("관리자 등록 테스트 카페");
    expect(detail.sourceType).toBe("SYSTEM");
    expect(detail.visibility).toBe("PUBLIC");
    expect(detail.businessHours).toEqual(body.businessHours);
    expect(detail.hoursExceptions).toEqual([{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "크리스마스 휴무" }]);
    expect(detail.imageUrl).toBe(uploaded.json.imageUrl);
    const after = await (await fetch(`${base}/api/v1/admin/places`)).json();
    expect(after).toHaveLength(before.length + 1);
    expect(Buffer.from(await (await fetch(`${base}${detail.imageUrl}`)).arrayBuffer())).toEqual(jpeg);
  });

  it("형식, 빈 파일, 용량, 없는 사진을 거부한다", async () => {
    await up();
    expect((await upload(jpeg, "image/png")).status).toBe(400);
    expect((await upload(Buffer.alloc(0), "image/jpeg")).status).toBe(400);
    expect((await upload(Buffer.from([0x00, 0x01]), "image/jpeg")).status).toBe(400);
    const tooBig = Buffer.alloc(10 * 1024 * 1024 + 1);
    tooBig[0] = 0xff;
    tooBig[1] = 0xd8;
    tooBig[2] = 0xff;
    expect((await upload(tooBig, "image/jpeg")).status).toBe(400);
    const exact = Buffer.alloc(10 * 1024 * 1024);
    exact[0] = 0xff;
    exact[1] = 0xd8;
    exact[2] = 0xff;
    expect((await upload(exact, "image/jpeg")).status).toBe(201);
    expect((await fetch(`${base}/api/v1/admin/places/mock-media/not-a-uuid`)).status).toBe(404);
    expect((await fetch(`${base}/api/v1/admin/places/mock-media/00000000-0000-4000-8000-000000000099`)).status).toBe(404);
  });
});
