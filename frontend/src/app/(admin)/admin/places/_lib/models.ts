import type { LatLng } from "../../../../../shared/lib/geo";

/**
 * 관리자 지도 관리에서 다루는 장소 출처.
 * 백엔드 PlaceSourceType에 있는 값만 알려진 출처입니다. KAKAO는 enum에 없어 넣지 않습니다.
 * 응답에 다른 문자열이 오면 그대로 두고, SYSTEM으로 바꾸거나 수정·삭제를 허용하지 않습니다.
 */
export const ADMIN_PLACE_SOURCE_TYPES = ["SYSTEM", "CUSTOM", "AI_EXTRACTED"] as const;

export type AdminPlaceSourceType = (typeof ADMIN_PLACE_SOURCE_TYPES)[number];

/**
 * 회원 업로드 MediaFile.MAX_SIZE_BYTES와 같은 상한입니다.
 * 관리자 전용 저장소 정책은 아직 없고, 이 값은 파일 선택 검증에만 씁니다.
 */
export const ADMIN_PLACE_PHOTO_MAX_BYTES = 10 * 1024 * 1024;

export const ADMIN_PLACE_PHOTO_MIME_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

export type AdminPlacePhotoMimeType = (typeof ADMIN_PLACE_PHOTO_MIME_TYPES)[number];

/** 요일별 영업 한 구간. 휴무가 아니면 시작·종료가 있고, 휴무면 시간은 영업으로 해석하지 않습니다. */
export interface AdminPlaceBusinessHours {
  dayOfWeek: number | null;
  openTime: number | null;
  closeTime: number | null;
  closed: boolean;
}

/** 특정 날짜의 예외. 휴무면 시간은 null입니다. */
export interface AdminPlaceHoursException {
  exceptionDate: string;
  closed: boolean;
  openTime: number | null;
  closeTime: number | null;
  note: string | null;
}

/** 목록 한 줄. 영업시간과 좌표는 상세에서만 다룹니다. */
export interface AdminPlaceSummary {
  id: string;
  name: string;
  address: string | null;
  category: string | null;
  sourceType: string;
  imageUrl: string | null;
}

export interface AdminPlaceDetail extends AdminPlaceSummary {
  /** 서버가 정한 공개 범위. 폼에서 바꾸지 않습니다. */
  visibility: string | null;
  location: LatLng | null;
  businessHours: AdminPlaceBusinessHours[];
  hoursExceptions: AdminPlaceHoursException[];
}

export interface AdminPlaceHourInput {
  dayOfWeek: number | null;
  closed: boolean;
  openTime: number | null;
  closeTime: number | null;
}

export interface AdminPlaceExceptionInput {
  exceptionDate: string;
  closed: boolean;
  openTime: number | null;
  closeTime: number | null;
  note: string;
}

/** 등록·수정 화면이 다루는 값. 설명·전화·지역·출처·공개 범위는 포함하지 않습니다. */
export interface AdminPlaceFormValues {
  name: string;
  address: string;
  category: string;
  location: LatLng | null;
  businessHours: AdminPlaceHourInput[];
  hoursExceptions: AdminPlaceExceptionInput[];
}

/**
 * 사진 저장 요청에 실리는 값.
 * File과 blob URL은 여기 넣지 않습니다. 파일을 고른 상태는 AdminPlacePhotoDraft입니다.
 * keep은 요청에서 필드를 빼고, delete는 null, set은 이미 저장된 주소입니다.
 */
export type AdminPlacePhotoChange =
  | { action: "keep" }
  | { action: "delete" }
  | { action: "set"; imageUrl: string };

export interface AdminPlaceCreateInput extends AdminPlaceFormValues {
  photo?: { action: "set"; imageUrl: string };
}

/** 필드가 없으면 서버 값을 유지합니다. 빈 영업시간 배열은 전체 제거입니다. */
export interface AdminPlaceUpdateInput {
  name?: string;
  address?: string;
  category?: string;
  location?: LatLng | null;
  businessHours?: AdminPlaceHourInput[];
  hoursExceptions?: AdminPlaceExceptionInput[];
  photo?: AdminPlacePhotoChange;
}

/** 브라우저에서만 있는 사진 선택. 새로고침 뒤의 blob URL은 저장된 사진이 아닙니다. */
export interface AdminPlacePhotoDraft {
  existingImageUrl: string | null;
  file: { type: string; size: number } | null;
  removed: boolean;
}

export interface AdminPlaceIssue {
  field: string;
  message: string;
}

export interface AdminPlaceValidation {
  valid: boolean;
  issues: AdminPlaceIssue[];
}

/** SYSTEM이면서, 공개 범위가 있으면 PUBLIC인 장소만 수정·삭제합니다. 알 수 없는 출처는 거부합니다. */
export function canManageAdminPlace(sourceType: string, visibility?: string | null): boolean {
  if (sourceType !== "SYSTEM") return false;
  return visibility == null || visibility === "PUBLIC";
}
