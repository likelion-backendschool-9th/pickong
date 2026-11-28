import type { IncomingMessage, ServerResponse } from "node:http";
import { adminPlaceMedia, adminPlaceMediaUrl, resetAdminPlaceMedia, saveAdminPlaceMedia } from "../lib/adminPlaceMedia.ts";

const PREFIX = "/api/v1/admin/places/mock-media";
const MAX_BYTES = 10 * 1024 * 1024;
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * 관리자 장소 사진 Mock. 실제 백엔드 미디어 API가 아닙니다.
 * JSON 라우터 앞에서 바이너리를 처리해 회원 미디어 저장소와 분리합니다.
 */
export async function handleAdminPlaceMedia(req: IncomingMessage, res: ServerResponse): Promise<boolean> {
  const url = new URL(req.url ?? "/", "http://localhost");
  if (url.pathname !== PREFIX && !url.pathname.startsWith(`${PREFIX}/`)) return false;

  if (req.method === "POST" && url.pathname === PREFIX) {
    await postMedia(req, res);
    return true;
  }
  if (req.method === "GET" && url.pathname.startsWith(`${PREFIX}/`)) {
    getMedia(url.pathname.slice(PREFIX.length + 1), res);
    return true;
  }
  sendJson(res, 404, { code: "NOT_FOUND", message: "사진을 찾을 수 없어요." });
  return true;
}

async function postMedia(req: IncomingMessage, res: ServerResponse) {
  const declared = Number(req.headers["content-length"]);
  if (Number.isFinite(declared) && declared > MAX_BYTES) {
    req.resume();
    sendJson(res, 400, { code: "INVALID_INPUT", message: "사진은 10MB까지 올릴 수 있어요." });
    return;
  }
  const bytes = await readLimited(req);
  if (bytes === "too-big") {
    sendJson(res, 400, { code: "INVALID_INPUT", message: "사진은 10MB까지 올릴 수 있어요." });
    return;
  }
  try {
    const saved = saveAdminPlaceMedia(bytes, String(req.headers["content-type"] ?? ""));
    sendJson(res, 201, { imageUrl: adminPlaceMediaUrl(saved.id) });
  } catch (error) {
    const code = error instanceof Error ? error.message : "";
    const message = code === "EMPTY" ? "빈 파일은 올릴 수 없어요." : code === "TOO_LARGE" ? "사진은 10MB까지 올릴 수 있어요." : "JPEG, PNG, WEBP 사진만 올릴 수 있어요.";
    sendJson(res, 400, { code: "INVALID_INPUT", message });
  }
}

function getMedia(id: string, res: ServerResponse) {
  if (!ID.test(id) || id.includes("/") || id.includes("..")) {
    sendJson(res, 404, { code: "NOT_FOUND", message: "사진을 찾을 수 없어요." });
    return;
  }
  const media = adminPlaceMedia(id);
  if (!media) {
    sendJson(res, 404, { code: "NOT_FOUND", message: "사진을 찾을 수 없어요." });
    return;
  }
  res.writeHead(200, { "Content-Type": media.contentType, "Content-Length": String(media.bytes.length) });
  res.end(media.bytes);
}

async function readLimited(req: IncomingMessage): Promise<Buffer | "too-big"> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buf.length;
    if (total > MAX_BYTES) {
      req.resume();
      return "too-big";
    }
    chunks.push(buf);
  }
  return Buffer.concat(chunks);
}

function sendJson(res: ServerResponse, status: number, data: unknown) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(data));
}

export { resetAdminPlaceMedia };
