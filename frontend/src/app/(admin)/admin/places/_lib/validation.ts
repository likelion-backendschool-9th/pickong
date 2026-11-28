import { ALL_CATEGORIES, type PlaceCategory } from "@/entities/place";
import { validateBusinessHours, validateHoursExceptions } from "./businessHours";
import { isStoredImageUrl, normalizeAdminPlaceText } from "./text";
import {
  ADMIN_PLACE_PHOTO_MAX_BYTES,
  ADMIN_PLACE_PHOTO_MIME_TYPES,
  type AdminPlaceCreateInput,
  type AdminPlaceExceptionInput,
  type AdminPlaceFormValues,
  type AdminPlaceHourInput,
  type AdminPlaceIssue,
  type AdminPlacePhotoDraft,
  type AdminPlacePhotoMimeType,
  type AdminPlaceUpdateInput,
  type AdminPlaceValidation,
} from "./models";

/**
 * 이름·주소의 DB 컬럼은 TEXT이고 길이 제한이 없습니다.
 * category는 VARCHAR(30)이지만 입력은 PlaceCategory만 받으므로 길이로 자르지 않습니다.
 * 예외 메모는 도메인과 Flyway에 길이 제한이 없어 여기서도 자르지 않습니다.
 */

function result(issues: AdminPlaceIssue[]): AdminPlaceValidation {
  return { valid: issues.length === 0, issues };
}

function issue(field: string, message: string): AdminPlaceIssue {
  return { field, message };
}

function nameIssue(name: string): AdminPlaceIssue | null {
  return normalizeAdminPlaceText(name) ? null : issue("name", "장소 이름을 입력해 주세요.");
}

function addressIssue(address: string): AdminPlaceIssue | null {
  return normalizeAdminPlaceText(address) ? null : issue("address", "주소를 입력해 주세요.");
}

export function isAdminPlaceCategory(value: string): value is PlaceCategory {
  return (ALL_CATEGORIES as readonly string[]).includes(value);
}

function categoryIssue(category: string): AdminPlaceIssue | null {
  const value = category.trim();
  if (!value) return issue("category", "장소 타입을 선택해 주세요.");
  if (!isAdminPlaceCategory(value)) return issue("category", "지원하지 않는 장소 타입이에요.");
  return null;
}

function coordinateIssues(latitude: number | null, longitude: number | null): AdminPlaceIssue[] {
  const issues: AdminPlaceIssue[] = [];
  if (latitude == null && longitude == null) {
    return [issue("latitude", "지도에서 위치를 지정해 주세요."), issue("longitude", "지도에서 위치를 지정해 주세요.")];
  }
  issues.push(...axisIssue("latitude", latitude, -90, 90, "위도는 -90부터 90 사이여야 해요."));
  issues.push(...axisIssue("longitude", longitude, -180, 180, "경도는 -180부터 180 사이여야 해요."));
  return issues;
}

function axisIssue(field: string, value: number | null, min: number, max: number, rangeMessage: string): AdminPlaceIssue[] {
  if (value == null) return [issue(field, "지도에서 위치를 지정해 주세요.")];
  if (!Number.isFinite(value)) return [issue(field, "좌표는 숫자여야 해요.")];
  if (value < min || value > max) return [issue(field, rangeMessage)];
  return [];
}

function hourInputs(rows: readonly AdminPlaceHourInput[] | undefined): AdminPlaceIssue[] {
  return rows ? validateBusinessHours(rows) : [];
}

function exceptionInputs(rows: readonly AdminPlaceExceptionInput[] | undefined): AdminPlaceIssue[] {
  return rows ? validateHoursExceptions(rows) : [];
}

/** 등록과 수정 폼의 전체 입력. 주소가 비어 있으면 저장할 수 없습니다. */
export function validateAdminPlaceForm(values: AdminPlaceFormValues): AdminPlaceValidation {
  const issues = [
    nameIssue(values.name),
    addressIssue(values.address),
    categoryIssue(values.category),
    ...coordinateIssues(values.location?.lat ?? null, values.location?.lng ?? null),
    ...hourInputs(values.businessHours),
    ...exceptionInputs(values.hoursExceptions),
  ].filter((item): item is AdminPlaceIssue => item != null);
  return result(issues);
}

export function validateAdminPlaceCreate(input: AdminPlaceCreateInput): AdminPlaceValidation {
  return validateAdminPlaceForm(input);
}

/** 있는 필드만 검사합니다. 빠진 필드는 기존 값 유지라 오류가 아닙니다. */
export function validateAdminPlaceUpdate(input: AdminPlaceUpdateInput): AdminPlaceValidation {
  const issues: AdminPlaceIssue[] = [];
  if (input.name !== undefined) {
    const name = nameIssue(input.name);
    if (name) issues.push(name);
  }
  if (input.address !== undefined) {
    const address = addressIssue(input.address);
    if (address) issues.push(address);
  }
  if (input.category !== undefined) {
    const category = categoryIssue(input.category);
    if (category) issues.push(category);
  }
  if (input.location !== undefined) {
    issues.push(...coordinateIssues(input.location?.lat ?? null, input.location?.lng ?? null));
  }
  issues.push(...hourInputs(input.businessHours), ...exceptionInputs(input.hoursExceptions));
  return result(issues);
}

export function isAdminPlacePhotoMime(value: string): value is AdminPlacePhotoMimeType {
  return (ADMIN_PLACE_PHOTO_MIME_TYPES as readonly string[]).includes(value);
}

/** 파일 형식·크기만 봅니다. 서버의 파일 내용 검사를 대신하지 않습니다. */
export function validateAdminPlacePhoto(file: { type: string; size: number }): AdminPlaceValidation {
  const type = file.type.trim().toLowerCase();
  if (!isAdminPlacePhotoMime(type)) {
    return result([issue("photo", "JPEG, PNG, WEBP 사진만 올릴 수 있어요.")]);
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    return result([issue("photo", "빈 파일은 올릴 수 없어요.")]);
  }
  if (file.size > ADMIN_PLACE_PHOTO_MAX_BYTES) {
    return result([issue("photo", "사진은 10MB까지 올릴 수 있어요.")]);
  }
  return result([]);
}

export type AdminPlacePhotoResolution =
  | { action: "keep" }
  | { action: "delete" }
  | { action: "upload"; file: { type: string; size: number } }
  | { action: "invalid"; issues: AdminPlaceIssue[] };

/**
 * 저장 요청 전의 사진 상태.
 * 새 파일이 있으면 교체, 없고 삭제를 눌렀으며 기존 URL이 있으면 삭제, 그 외에는 유지입니다.
 * upload는 아직 저장 주소가 없으므로 요청 DTO에 파일을 넣지 않습니다.
 */
export function resolveAdminPlacePhoto(draft: AdminPlacePhotoDraft): AdminPlacePhotoResolution {
  if (draft.file) {
    const checked = validateAdminPlacePhoto(draft.file);
    if (!checked.valid) return { action: "invalid", issues: checked.issues };
    return { action: "upload", file: draft.file };
  }
  if (draft.removed && draft.existingImageUrl) return { action: "delete" };
  return { action: "keep" };
}

export { isStoredImageUrl, normalizeAdminPlaceText };
