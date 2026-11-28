import type { LatLng } from "@/shared/lib/geo";
import type {
  AdminPlaceBusinessHoursDto,
  AdminPlaceCreateRequestDto,
  AdminPlaceDetailDto,
  AdminPlaceHoursExceptionDto,
  AdminPlaceSummaryDto,
  AdminPlaceUpdateRequestDto,
} from "./dto";
import type {
  AdminPlaceBusinessHours,
  AdminPlaceCreateInput,
  AdminPlaceDetail,
  AdminPlaceExceptionInput,
  AdminPlaceFormValues,
  AdminPlaceHourInput,
  AdminPlaceHoursException,
  AdminPlacePhotoChange,
  AdminPlaceSummary,
  AdminPlaceUpdateInput,
} from "../_lib/models";
import { isStoredImageUrl, normalizeAdminPlaceText } from "../_lib/validation";

function text(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function textOrNull(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function finite(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function location(lat: unknown, lng: unknown): LatLng | null {
  const latitude = finite(lat);
  const longitude = finite(lng);
  if (latitude == null || longitude == null) return null;
  return { lat: latitude, lng: longitude };
}

function hours(dto: AdminPlaceBusinessHoursDto): AdminPlaceBusinessHours {
  return {
    dayOfWeek: finite(dto?.dayOfWeek),
    openTime: finite(dto?.openTime),
    closeTime: finite(dto?.closeTime),
    closed: dto?.closed === true,
  };
}

function exception(dto: AdminPlaceHoursExceptionDto): AdminPlaceHoursException {
  return {
    exceptionDate: text(dto?.exceptionDate),
    closed: dto?.closed === true,
    openTime: finite(dto?.openTime),
    closeTime: finite(dto?.closeTime),
    note: textOrNull(dto?.note),
  };
}

export function toAdminPlaceSummary(dto: AdminPlaceSummaryDto): AdminPlaceSummary {
  return {
    id: text(dto?.id),
    name: text(dto?.name),
    address: textOrNull(dto?.address),
    category: textOrNull(dto?.category),
    sourceType: text(dto?.sourceType),
    imageUrl: textOrNull(dto?.imageUrl),
  };
}

export function toAdminPlaceDetail(dto: AdminPlaceDetailDto): AdminPlaceDetail {
  return {
    ...toAdminPlaceSummary(dto),
    visibility: textOrNull(dto?.visibility),
    location: location(dto?.lat, dto?.lng),
    businessHours: Array.isArray(dto?.businessHours) ? dto.businessHours.map(hours) : [],
    hoursExceptions: Array.isArray(dto?.hoursExceptions) ? dto.hoursExceptions.map(exception) : [],
  };
}

export function toAdminPlaceFormValues(place: AdminPlaceDetail): AdminPlaceFormValues {
  return {
    name: place.name,
    address: place.address ?? "",
    category: place.category ?? "",
    location: place.location,
    businessHours: place.businessHours.map((row) => ({
      dayOfWeek: row.dayOfWeek,
      closed: row.closed,
      openTime: row.openTime,
      closeTime: row.closeTime,
    })),
    hoursExceptions: place.hoursExceptions.map((row) => ({
      exceptionDate: row.exceptionDate,
      closed: row.closed,
      openTime: row.openTime,
      closeTime: row.closeTime,
      note: row.note ?? "",
    })),
  };
}

function storedImageUrl(url: string): string {
  if (!isStoredImageUrl(url)) {
    throw new Error("브라우저 임시 주소는 장소 저장 요청에 넣을 수 없습니다.");
  }
  return url;
}

/** 휴무 행의 open_time·close_time은 NOT NULL이라 0을 넣습니다. 이 0은 영업 구간이 아닙니다. */
function toHoursDto(row: AdminPlaceHourInput): AdminPlaceBusinessHoursDto {
  if (row.closed) {
    if (row.dayOfWeek == null) throw new Error("휴무 요일을 정하지 않아 영업시간 요청을 만들 수 없습니다.");
    return { dayOfWeek: row.dayOfWeek, openTime: 0, closeTime: 0, closed: true };
  }
  if (row.dayOfWeek == null || row.openTime == null || row.closeTime == null) {
    throw new Error("영업 중인 요일의 시작·종료 시간이 없습니다.");
  }
  return { dayOfWeek: row.dayOfWeek, openTime: row.openTime, closeTime: row.closeTime, closed: false };
}

/** 예외 휴무의 시간은 NULL 가능하므로 휴무면 null로 보냅니다. */
function toExceptionDto(row: AdminPlaceExceptionInput): AdminPlaceHoursExceptionDto {
  const note = normalizeAdminPlaceText(row.note);
  if (row.closed) {
    return { exceptionDate: row.exceptionDate, closed: true, openTime: null, closeTime: null, note: note || null };
  }
  if (row.openTime == null || row.closeTime == null) {
    throw new Error("예외 영업일의 시작·종료 시간이 없습니다.");
  }
  return {
    exceptionDate: row.exceptionDate,
    closed: false,
    openTime: row.openTime,
    closeTime: row.closeTime,
    note: note || null,
  };
}

function applyPhoto(body: { imageUrl?: string | null }, photo: AdminPlacePhotoChange | undefined, allowDelete: boolean) {
  if (!photo || photo.action === "keep") return;
  if (photo.action === "delete") {
    if (!allowDelete) return;
    body.imageUrl = null;
    return;
  }
  body.imageUrl = storedImageUrl(photo.imageUrl);
}

export function toAdminPlaceCreateRequest(input: AdminPlaceCreateInput): AdminPlaceCreateRequestDto {
  if (input.location == null) throw new Error("좌표가 없어 장소 등록 요청을 만들 수 없습니다.");
  const body: AdminPlaceCreateRequestDto = {
    name: normalizeAdminPlaceText(input.name),
    address: normalizeAdminPlaceText(input.address),
    category: input.category.trim(),
    lat: input.location.lat,
    lng: input.location.lng,
    businessHours: input.businessHours.map(toHoursDto),
    hoursExceptions: input.hoursExceptions.map(toExceptionDto),
  };
  applyPhoto(body, input.photo, false);
  return body;
}

export function toAdminPlaceUpdateInput(form: AdminPlaceFormValues, photo: AdminPlacePhotoChange = { action: "keep" }): AdminPlaceUpdateInput {
  return {
    name: form.name,
    address: form.address,
    category: form.category,
    location: form.location,
    businessHours: form.businessHours,
    hoursExceptions: form.hoursExceptions,
    photo,
  };
}

export function toAdminPlaceUpdateRequest(input: AdminPlaceUpdateInput): AdminPlaceUpdateRequestDto {
  const body: AdminPlaceUpdateRequestDto = {};
  if (input.name !== undefined) body.name = normalizeAdminPlaceText(input.name);
  if (input.address !== undefined) body.address = normalizeAdminPlaceText(input.address);
  if (input.category !== undefined) body.category = input.category.trim();
  if (input.location) {
    body.lat = input.location.lat;
    body.lng = input.location.lng;
  }
  if (input.businessHours !== undefined) body.businessHours = input.businessHours.map(toHoursDto);
  if (input.hoursExceptions !== undefined) body.hoursExceptions = input.hoursExceptions.map(toExceptionDto);
  applyPhoto(body, input.photo, true);
  return body;
}
