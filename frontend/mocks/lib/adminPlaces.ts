import { randomUUID } from "node:crypto";
import type { AdminPlaceBusinessHoursDto, AdminPlaceDetailDto, AdminPlaceHoursExceptionDto, AdminPlaceSummaryDto } from "../../src/app/(admin)/admin/places/_api/dto.ts";
import { validateBusinessHours, validateHoursExceptions } from "../../src/app/(admin)/admin/places/_lib/businessHours.ts";
import type { AdminPlaceExceptionInput, AdminPlaceHourInput } from "../../src/app/(admin)/admin/places/_lib/models.ts";
import { isStoredImageUrl, normalizeAdminPlaceText } from "../../src/app/(admin)/admin/places/_lib/text.ts";
import { ALL_CATEGORIES } from "../../src/entities/place/categories.ts";
import { adminPlaceFixtures, type AdminPlaceRecord } from "../fixtures/adminPlaces.ts";
import { HttpError } from "../router.ts";

/**
 * 관리자 장소 Mock 저장소.
 * 회원 db.places와 분리되어 있어 여기서 등록한 장소는 일반 지도 Mock에 나타나지 않습니다.
 * 프로세스를 다시 시작하면 fixture로 돌아갑니다. 파일이나 브라우저 저장소에는 쓰지 않습니다.
 */
let places: AdminPlaceRecord[] = structuredClone(adminPlaceFixtures);

export function resetAdminPlaces() {
  places = structuredClone(adminPlaceFixtures);
}

export function adminPlaceRecord(id: string): AdminPlaceRecord | undefined {
  const row = places.find((place) => place.id === id);
  return row ? structuredClone(row) : undefined;
}

function invalid(message: string): never {
  throw new HttpError(400, "INVALID_INPUT", message);
}

function notFound(): never {
  throw new HttpError(404, "PLACE_NOT_FOUND", "장소를 찾을 수 없어요.");
}

function notManageable(): never {
  throw new HttpError(409, "PLACE_NOT_MANAGEABLE", "SYSTEM 장소만 수정할 수 있어요.");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function listed(row: AdminPlaceRecord) {
  return row.deletedAt == null && row.visibility === "PUBLIC";
}

function readable(row: AdminPlaceRecord | undefined): AdminPlaceRecord {
  if (!row || !listed(row)) notFound();
  return row;
}

function copyHours(rows: AdminPlaceBusinessHoursDto[]): AdminPlaceBusinessHoursDto[] {
  return rows.map((row) => ({ ...row }));
}

function copyExceptions(rows: AdminPlaceHoursExceptionDto[]): AdminPlaceHoursExceptionDto[] {
  return rows.map((row) => ({ ...row }));
}

export function toAdminPlaceSummaryDto(row: AdminPlaceRecord): AdminPlaceSummaryDto {
  return {
    id: row.id,
    name: row.name,
    address: row.address,
    category: row.category,
    sourceType: row.sourceType,
    imageUrl: row.imageUrl,
  };
}

export function toAdminPlaceDetailDto(row: AdminPlaceRecord): AdminPlaceDetailDto {
  return {
    ...toAdminPlaceSummaryDto(row),
    visibility: row.visibility,
    lat: row.lat,
    lng: row.lng,
    businessHours: copyHours(row.businessHours),
    hoursExceptions: copyExceptions(row.hoursExceptions),
  };
}

export function listAdminPlaceSummaries(): AdminPlaceSummaryDto[] {
  return places.filter(listed).map(toAdminPlaceSummaryDto);
}

export function getAdminPlaceDetail(id: string): AdminPlaceDetailDto {
  return toAdminPlaceDetailDto(readable(places.find((place) => place.id === id)));
}

function textIssue(value: unknown, message: string): string {
  if (typeof value !== "string" || !normalizeAdminPlaceText(value)) invalid(message);
  return normalizeAdminPlaceText(value);
}

function categoryOf(value: unknown): string {
  if (typeof value !== "string" || !value.trim()) invalid("장소 타입을 선택해 주세요.");
  const category = value.trim();
  if (!(ALL_CATEGORIES as readonly string[]).includes(category)) invalid("지원하지 않는 장소 타입이에요.");
  return category;
}

function axis(value: unknown, min: number, max: number, rangeMessage: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) invalid("좌표는 숫자여야 해요.");
  if (value < min || value > max) invalid(rangeMessage);
  return value;
}

function coordinate(lat: unknown, lng: unknown) {
  return {
    lat: axis(lat, -90, 90, "위도는 -90부터 90 사이여야 해요."),
    lng: axis(lng, -180, 180, "경도는 -180부터 180 사이여야 해요."),
  };
}

function hourInput(value: unknown): AdminPlaceHourInput {
  if (!isRecord(value)) invalid("영업시간을 확인해 주세요.");
  return {
    dayOfWeek: typeof value.dayOfWeek === "number" ? value.dayOfWeek : null,
    closed: value.closed === true,
    openTime: typeof value.openTime === "number" ? value.openTime : null,
    closeTime: typeof value.closeTime === "number" ? value.closeTime : null,
  };
}

function exceptionInput(value: unknown): AdminPlaceExceptionInput {
  if (!isRecord(value) || typeof value.exceptionDate !== "string") invalid("날짜를 확인해 주세요.");
  if (value.note != null && typeof value.note !== "string") invalid("예외 영업 메모를 확인해 주세요.");
  return {
    exceptionDate: value.exceptionDate,
    closed: value.closed === true,
    openTime: typeof value.openTime === "number" ? value.openTime : value.openTime == null ? null : Number.NaN,
    closeTime: typeof value.closeTime === "number" ? value.closeTime : value.closeTime == null ? null : Number.NaN,
    note: typeof value.note === "string" ? value.note : "",
  };
}

function hoursFrom(value: unknown): AdminPlaceBusinessHoursDto[] {
  if (!Array.isArray(value)) invalid("영업시간을 확인해 주세요.");
  const inputs = value.map(hourInput);
  const issue = validateBusinessHours(inputs)[0];
  if (issue) invalid(issue.message);
  return inputs.map((row) =>
    row.closed
      ? { dayOfWeek: row.dayOfWeek as number, openTime: 0, closeTime: 0, closed: true }
      : { dayOfWeek: row.dayOfWeek as number, openTime: row.openTime as number, closeTime: row.closeTime as number, closed: false },
  );
}

function exceptionsFrom(value: unknown): AdminPlaceHoursExceptionDto[] {
  if (!Array.isArray(value)) invalid("예외 영업시간을 확인해 주세요.");
  const inputs = value.map(exceptionInput);
  const issue = validateHoursExceptions(inputs)[0];
  if (issue) invalid(issue.message);
  return inputs.map((row) => {
    const note = normalizeAdminPlaceText(row.note);
    if (row.closed) {
      return { exceptionDate: row.exceptionDate, closed: true, openTime: null, closeTime: null, note: note || null };
    }
    return {
      exceptionDate: row.exceptionDate,
      closed: false,
      openTime: row.openTime,
      closeTime: row.closeTime,
      note: note || null,
    };
  });
}

function imageFrom(value: unknown, allowNull: boolean): string | null {
  if (value == null) {
    if (allowNull) return null;
    invalid("저장할 수 있는 사진 주소가 아니에요.");
  }
  if (typeof value !== "string" || !isStoredImageUrl(value)) invalid("저장할 수 있는 사진 주소가 아니에요.");
  return value;
}

function bodyOf(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) invalid("요청 형식을 확인해 주세요.");
  return value;
}

function has(body: Record<string, unknown>, key: string) {
  return Object.prototype.hasOwnProperty.call(body, key);
}

export function createAdminPlace(value: unknown): AdminPlaceDetailDto {
  const body = bodyOf(value);
  const name = textIssue(body.name, "장소 이름을 입력해 주세요.");
  const address = textIssue(body.address, "주소를 입력해 주세요.");
  const category = categoryOf(body.category);
  if (!has(body, "lat") || !has(body, "lng")) invalid("지도에서 위치를 지정해 주세요.");
  const location = coordinate(body.lat, body.lng);
  const businessHours = has(body, "businessHours") ? hoursFrom(body.businessHours) : [];
  const hoursExceptions = has(body, "hoursExceptions") ? exceptionsFrom(body.hoursExceptions) : [];
  const imageUrl = has(body, "imageUrl") ? imageFrom(body.imageUrl, false) : null;
  const row: AdminPlaceRecord = {
    id: randomUUID(),
    name,
    address,
    category,
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: location.lat,
    lng: location.lng,
    imageUrl,
    businessHours,
    hoursExceptions,
    deletedAt: null,
    description: null,
    phone: null,
    region: null,
  };
  places.push(row);
  return toAdminPlaceDetailDto(row);
}

export function updateAdminPlace(id: string, value: unknown): AdminPlaceDetailDto {
  const current = places.find((place) => place.id === id);
  const row = readable(current);
  if (row.sourceType !== "SYSTEM") notManageable();
  const body = value === undefined ? {} : bodyOf(value);
  const next: AdminPlaceRecord = structuredClone(row);
  if (has(body, "name")) next.name = textIssue(body.name, "장소 이름을 입력해 주세요.");
  if (has(body, "address")) next.address = textIssue(body.address, "주소를 입력해 주세요.");
  if (has(body, "category")) next.category = categoryOf(body.category);
  if (has(body, "lat") || has(body, "lng")) {
    const location = coordinate(has(body, "lat") ? body.lat : row.lat, has(body, "lng") ? body.lng : row.lng);
    next.lat = location.lat;
    next.lng = location.lng;
  }
  if (has(body, "businessHours")) next.businessHours = hoursFrom(body.businessHours);
  if (has(body, "hoursExceptions")) next.hoursExceptions = exceptionsFrom(body.hoursExceptions);
  if (has(body, "imageUrl")) next.imageUrl = imageFrom(body.imageUrl, true);
  const index = places.findIndex((place) => place.id === id);
  places[index] = next;
  return toAdminPlaceDetailDto(next);
}

export function deleteAdminPlace(id: string) {
  const current = places.find((place) => place.id === id);
  const row = readable(current);
  if (row.sourceType !== "SYSTEM") notManageable();
  const index = places.findIndex((place) => place.id === id);
  places[index] = { ...row, deletedAt: new Date().toISOString() };
}
