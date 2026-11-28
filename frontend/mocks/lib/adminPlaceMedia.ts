import { randomUUID } from "node:crypto";

const MAX_BYTES = 10 * 1024 * 1024;

export interface AdminPlaceMediaRecord {
  id: string;
  bytes: Buffer;
  contentType: "image/jpeg" | "image/png" | "image/webp";
}

const images = new Map<string, AdminPlaceMediaRecord>();

export function resetAdminPlaceMedia() {
  images.clear();
}

export function adminPlaceMedia(id: string): AdminPlaceMediaRecord | undefined {
  return images.get(id);
}

export function sniffAdminPlaceImage(bytes: Buffer): AdminPlaceMediaRecord["contentType"] | null {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return "image/png";
  if (bytes.length >= 12 && bytes.subarray(0, 4).toString("ascii") === "RIFF" && bytes.subarray(8, 12).toString("ascii") === "WEBP") return "image/webp";
  return null;
}

export function adminPlaceMediaUrl(id: string) {
  return `/api/v1/admin/places/mock-media/${id}`;
}

export function saveAdminPlaceMedia(bytes: Buffer, declaredType: string): AdminPlaceMediaRecord {
  if (bytes.length === 0) throw new Error("EMPTY");
  if (bytes.length > MAX_BYTES) throw new Error("TOO_LARGE");
  const sniffed = sniffAdminPlaceImage(bytes);
  const declared = declaredType.split(";")[0]?.trim().toLowerCase();
  if (!sniffed || sniffed !== declared) throw new Error("TYPE");
  const record = { id: randomUUID(), bytes, contentType: sniffed };
  images.set(record.id, record);
  return record;
}
