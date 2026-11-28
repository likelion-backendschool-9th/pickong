import type { AdminPlaceBusinessHoursDto, AdminPlaceHoursExceptionDto } from "../../src/app/(admin)/admin/places/_api/dto.ts";

/**
 * 관리자 지도 Mock 전용 장소.
 * id는 public_id에 해당하는 UUID이며, 회원 장소 fixture의 p-* id와 겹치지 않습니다.
 * 좌표와 영업시간은 화면 확인용 예시입니다.
 */
export interface AdminPlaceRecord {
  id: string;
  name: string;
  address: string | null;
  category: string;
  sourceType: string;
  visibility: string;
  lat: number;
  lng: number;
  imageUrl: string | null;
  businessHours: AdminPlaceBusinessHoursDto[];
  hoursExceptions: AdminPlaceHoursExceptionDto[];
  deletedAt: string | null;
  description: string | null;
  phone: string | null;
  region: string | null;
}

export const ADMIN_PLACE_IDS = {
  systemHours: "11111111-1111-4111-8111-111111111111",
  systemClosed: "22222222-2222-4222-8222-222222222222",
  systemAllDay: "33333333-3333-4333-8333-333333333333",
  systemOvernight: "44444444-4444-4444-8444-444444444444",
  systemEmpty: "55555555-5555-4555-8555-555555555555",
  customPublic: "66666666-6666-4666-8666-666666666666",
  aiExtracted: "77777777-7777-4777-8777-777777777777",
  customPrivate: "88888888-8888-4888-8888-888888888888",
  systemDeleted: "99999999-9999-4999-8999-999999999999",
  unknownSource: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
} as const;

const kept = {
  description: "관리자 폼에 없는 설명",
  phone: "02-000-0000",
  region: "서울",
};

export const adminPlaceFixtures: AdminPlaceRecord[] = [
  {
    id: ADMIN_PLACE_IDS.systemHours,
    name: "찜콩 카페",
    address: "서울 성동구 성수동",
    category: "CAFE",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 37.5446,
    lng: 127.0559,
    imageUrl: "/images/photos/cafe.jpg",
    businessHours: [{ dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false }],
    hoursExceptions: [],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.systemClosed,
    name: "찜콩 식당",
    address: "서울 종로구 익선동",
    category: "RESTAURANT",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 37.574,
    lng: 126.99,
    imageUrl: null,
    businessHours: [
      { dayOfWeek: 0, openTime: 0, closeTime: 0, closed: true },
      { dayOfWeek: 1, openTime: 660, closeTime: 1200, closed: false },
    ],
    hoursExceptions: [{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "임시 휴무" }],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.systemAllDay,
    name: "찜콩 편의",
    address: "대전 서구 둔산동",
    category: "SHOP",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 36.351,
    lng: 127.378,
    imageUrl: null,
    businessHours: [{ dayOfWeek: 2, openTime: 0, closeTime: 1440, closed: false }],
    hoursExceptions: [],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.systemOvernight,
    name: "찜콩 야간",
    address: "부산 해운대구",
    category: "ATTRACTION",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 35.163,
    lng: 129.163,
    imageUrl: null,
    businessHours: [{ dayOfWeek: 5, openTime: 1080, closeTime: 1560, closed: false }],
    hoursExceptions: [{ exceptionDate: "2026-05-05", closed: false, openTime: 600, closeTime: 900, note: "단축 영업" }],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.systemEmpty,
    name: "주소 없는 장소",
    address: null,
    category: "ETC",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 37.5,
    lng: 127.0,
    imageUrl: null,
    businessHours: [],
    hoursExceptions: [],
    deletedAt: null,
    description: "주소 없음",
    phone: null,
    region: null,
  },
  {
    id: ADMIN_PLACE_IDS.customPublic,
    name: "승인된 제보 장소",
    address: "서울 마포구",
    category: "PARK",
    sourceType: "CUSTOM",
    visibility: "PUBLIC",
    lat: 37.55,
    lng: 126.91,
    imageUrl: null,
    businessHours: [],
    hoursExceptions: [],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.aiExtracted,
    name: "추출된 장소",
    address: "서울 용산구",
    category: "EXHIBITION",
    sourceType: "AI_EXTRACTED",
    visibility: "PUBLIC",
    lat: 37.53,
    lng: 126.97,
    imageUrl: null,
    businessHours: [],
    hoursExceptions: [],
    deletedAt: null,
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.customPrivate,
    name: "개인 장소",
    address: "비공개 주소",
    category: "CAFE",
    sourceType: "CUSTOM",
    visibility: "PRIVATE",
    lat: 37.4,
    lng: 127.1,
    imageUrl: null,
    businessHours: [],
    hoursExceptions: [],
    deletedAt: null,
    description: "회원만 보는 장소",
    phone: null,
    region: null,
  },
  {
    id: ADMIN_PLACE_IDS.systemDeleted,
    name: "삭제된 시스템 장소",
    address: "서울 강남구",
    category: "STREET",
    sourceType: "SYSTEM",
    visibility: "PUBLIC",
    lat: 37.5,
    lng: 127.03,
    imageUrl: "/images/photos/cafe.jpg",
    businessHours: [],
    hoursExceptions: [],
    deletedAt: "2026-11-01T00:00:00.000Z",
    ...kept,
  },
  {
    id: ADMIN_PLACE_IDS.unknownSource,
    name: "출처를 알 수 없는 장소",
    address: "서울 중구",
    category: "PHOTO_SPOT",
    sourceType: "KAKAO",
    visibility: "PUBLIC",
    lat: 37.56,
    lng: 126.98,
    imageUrl: null,
    businessHours: [],
    hoursExceptions: [],
    deletedAt: null,
    ...kept,
  },
];
