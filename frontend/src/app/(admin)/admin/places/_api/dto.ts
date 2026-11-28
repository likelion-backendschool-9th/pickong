/**
 * 관리자 장소 Mock·프론트 계약입니다. 백엔드에 이 경로가 구현되어 있지 않습니다.
 * {placeId}는 places.public_id UUID입니다. 제보 API의 bigint id와 다릅니다.
 * 목록은 다른 관리자 API처럼 페이지 없는 배열입니다.
 * 공개이고 삭제되지 않은 장소만 온다고 가정하며, 회원 비공개 장소는 포함하지 않습니다.
 */

export interface AdminPlaceBusinessHoursDto {
  dayOfWeek: number;
  openTime: number;
  closeTime: number;
  closed: boolean;
}

export interface AdminPlaceHoursExceptionDto {
  exceptionDate: string;
  closed: boolean;
  openTime: number | null;
  closeTime: number | null;
  note: string | null;
}

export interface AdminPlaceSummaryDto {
  id: string;
  name: string;
  address: string | null;
  category: string | null;
  sourceType: string;
  imageUrl: string | null;
}

export interface AdminPlaceDetailDto extends AdminPlaceSummaryDto {
  visibility: string | null;
  lat: number;
  lng: number;
  businessHours: AdminPlaceBusinessHoursDto[];
  hoursExceptions: AdminPlaceHoursExceptionDto[];
}

/** 등록 본문. sourceType·visibility는 서버가 SYSTEM·PUBLIC으로 정하므로 넣지 않습니다. */
export interface AdminPlaceCreateRequestDto {
  name: string;
  address: string;
  category: string;
  lat: number;
  lng: number;
  businessHours: AdminPlaceBusinessHoursDto[];
  hoursExceptions: AdminPlaceHoursExceptionDto[];
  imageUrl?: string;
}

/**
 * 수정 본문.
 * 키를 빼면 유지, 영업시간 배열이 비어 있으면 해당 묶음 전체 제거, imageUrl null은 대표 사진 삭제입니다.
 * 설명·전화·지역처럼 이 폼에 없는 컬럼은 키 자체를 보내지 않습니다.
 */
export interface AdminPlaceUpdateRequestDto {
  name?: string;
  address?: string;
  category?: string;
  lat?: number;
  lng?: number;
  businessHours?: AdminPlaceBusinessHoursDto[];
  hoursExceptions?: AdminPlaceHoursExceptionDto[];
  imageUrl?: string | null;
}
