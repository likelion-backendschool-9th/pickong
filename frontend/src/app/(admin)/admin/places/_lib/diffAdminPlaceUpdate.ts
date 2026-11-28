import type { LatLng } from "../../../../../shared/lib/geo";
import { normalizeAdminPlaceText } from "./text";
import type {
  AdminPlaceExceptionInput,
  AdminPlaceFormValues,
  AdminPlaceHourInput,
  AdminPlacePhotoChange,
  AdminPlaceUpdateInput,
} from "./models";

function sameLocation(left: LatLng | null, right: LatLng | null) {
  if (left == null || right == null) return left == null && right == null;
  return left.lat === right.lat && left.lng === right.lng;
}

function hourKey(row: AdminPlaceHourInput) {
  return `${row.dayOfWeek}:${row.closed}:${row.openTime}:${row.closeTime}`;
}

function sameRows(left: readonly string[], right: readonly string[]) {
  if (left.length !== right.length) return false;
  const sortedLeft = [...left].sort();
  const sortedRight = [...right].sort();
  return sortedLeft.every((value, index) => value === sortedRight[index]);
}

function sameHours(left: readonly AdminPlaceHourInput[], right: readonly AdminPlaceHourInput[]) {
  return sameRows(left.map(hourKey), right.map(hourKey));
}

function exceptionKey(row: AdminPlaceExceptionInput) {
  return `${row.exceptionDate}:${row.closed}:${row.openTime}:${row.closeTime}:${normalizeAdminPlaceText(row.note)}`;
}

function sameExceptions(left: readonly AdminPlaceExceptionInput[], right: readonly AdminPlaceExceptionInput[]) {
  return sameRows(left.map(exceptionKey), right.map(exceptionKey));
}

/**
 * 서버에 저장된 폼 값과 현재 입력을 비교해 바뀐 필드만 고릅니다.
 * 배열 순서는 바꾸지 않고, 원본 배열도 정렬하지 않습니다.
 * 사진 keep은 키를 만들지 않습니다.
 */
export function diffAdminPlaceUpdate(
  baseline: AdminPlaceFormValues,
  current: AdminPlaceFormValues,
  photo: AdminPlacePhotoChange = { action: "keep" },
): AdminPlaceUpdateInput {
  const patch: AdminPlaceUpdateInput = {};
  if (normalizeAdminPlaceText(current.name) !== normalizeAdminPlaceText(baseline.name)) patch.name = current.name;
  if (normalizeAdminPlaceText(current.address) !== normalizeAdminPlaceText(baseline.address)) patch.address = current.address;
  if (current.category.trim() !== baseline.category.trim()) patch.category = current.category;
  if (!sameLocation(baseline.location, current.location)) patch.location = current.location;
  if (!sameHours(baseline.businessHours, current.businessHours)) patch.businessHours = current.businessHours;
  if (!sameExceptions(baseline.hoursExceptions, current.hoursExceptions)) patch.hoursExceptions = current.hoursExceptions;
  if (photo.action !== "keep") patch.photo = photo;
  return patch;
}

export function hasAdminPlaceChanges(input: AdminPlaceUpdateInput) {
  return (
    input.name !== undefined ||
    input.address !== undefined ||
    input.category !== undefined ||
    input.location !== undefined ||
    input.businessHours !== undefined ||
    input.hoursExceptions !== undefined ||
    (input.photo != null && input.photo.action !== "keep")
  );
}
