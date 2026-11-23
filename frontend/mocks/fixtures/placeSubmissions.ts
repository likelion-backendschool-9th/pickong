import type { PlaceCategory } from "./types.ts";

export type SubmissionStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface PlaceSubmissionRow {
  id: string;
  name: string;
  category: PlaceCategory;
  address: string;
  region: string;
  lat: number;
  lng: number;
  description: string;
  imageUrl: string | null;
  submitterId: string;
  submittedAt: string;
  status: SubmissionStatus;
  reviewedAt: string | null;
  rejectReason: string | null;
  /** 승인되어 db.places에 들어간 장소 id */
  placeId: string | null;
}

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

/**
 * 장소 제보 시드. 심사 대기 5, 승인 3, 반려 2.
 * 승인 건의 placeId는 서버가 뜰 때 db.places에도 넣습니다.
 * mock 서버를 다시 시작하면 이 값으로 돌아갑니다.
 */
export const placeSubmissions: PlaceSubmissionRow[] = [
  {
    id: "ps-1",
    name: "연남 골목 책방",
    category: "SHOP",
    address: "서울 마포구 연남동 · 제보된 좌표",
    region: "연남동",
    lat: 37.5628,
    lng: 126.9254,
    description: "골목 안쪽 노란 간판. 저녁에는 창으로 불이 새어 나와요.",
    imageUrl: "/images/photos/shop.jpg",
    submitterId: "u-seoyeon",
    submittedAt: ago(0.2),
    status: "PENDING",
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
  },
  {
    id: "ps-2",
    name: "서울숲 메타세쿼이아 벤치",
    category: "PARK",
    address: "서울 성동구 서울숲 · 제보된 좌표",
    region: "서울숲",
    lat: 37.5441,
    lng: 127.0376,
    description: "나무 사이 긴 벤치. 오후에 그늘이 좋아요.",
    imageUrl: "/images/photos/park.jpg",
    submitterId: "u-minsu",
    submittedAt: ago(0.6),
    status: "PENDING",
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
  },
  {
    id: "ps-3",
    name: "을지로 네온 골목",
    category: "STREET",
    address: "서울 중구 을지로 · 제보된 좌표",
    region: "을지로",
    lat: 37.5662,
    lng: 126.991,
    description: "인쇄소 골목 끝, 파란 네온이 있는 모퉁이.",
    imageUrl: null,
    submitterId: "u-haeun",
    submittedAt: ago(1.1),
    status: "PENDING",
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
  },
  {
    id: "ps-4",
    name: "익선동 한옥 카페",
    category: "CAFE",
    address: "서울 종로구 익선동 · 제보된 좌표",
    region: "익선동",
    lat: 37.574,
    lng: 126.9898,
    description: "대문 안쪽 마당에 좌석이 있는 작은 카페.",
    imageUrl: "/images/photos/cafe.jpg",
    submitterId: "u-yujin",
    submittedAt: ago(1.8),
    status: "PENDING",
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
  },
  {
    id: "ps-5",
    name: "망원 시장 어귀",
    category: "RESTAURANT",
    address: "서울 마포구 망원동 · 제보된 좌표",
    region: "망원동",
    lat: 37.5564,
    lng: 126.9102,
    description: "시장 입구 오른쪽, 줄 서는 튀김 가게.",
    imageUrl: null,
    submitterId: "u-walker",
    submittedAt: ago(2.4),
    status: "PENDING",
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
  },
  {
    id: "ps-6",
    name: "뚝섬 노을 데크",
    category: "PHOTO_SPOT",
    address: "서울 광진구 자양동 · 제보된 좌표",
    region: "뚝섬",
    lat: 37.5292,
    lng: 127.0668,
    description: "한강 데크에서 노을이 잘 보이는 자리.",
    imageUrl: "/images/photos/street.jpg",
    submitterId: "u-jimin",
    submittedAt: ago(6),
    status: "APPROVED",
    reviewedAt: ago(4),
    rejectReason: null,
    placeId: "p-sub-ttukseom",
  },
  {
    id: "ps-7",
    name: "성수 대림창고 앞",
    category: "EXHIBITION",
    address: "서울 성동구 성수동 · 제보된 좌표",
    region: "성수동",
    lat: 37.5474,
    lng: 127.0472,
    description: "창고 건물 앞 광장. 주말에 작은 전시가 열려요.",
    imageUrl: "/images/photos/shop.jpg",
    submitterId: "u-minsu",
    submittedAt: ago(9),
    status: "APPROVED",
    reviewedAt: ago(7),
    rejectReason: null,
    placeId: "p-sub-daerim",
  },
  {
    id: "ps-8",
    name: "잠원 피크닉 잔디",
    category: "PARK",
    address: "서울 서초구 잠원동 · 제보된 좌표",
    region: "잠원",
    lat: 37.5205,
    lng: 127.0112,
    description: "그늘이 적은 잔디 언덕. 해가 긴 오후에 좋아요.",
    imageUrl: null,
    submitterId: "u-haeun",
    submittedAt: ago(14),
    status: "APPROVED",
    reviewedAt: ago(11),
    rejectReason: null,
    placeId: "p-sub-jamwon",
  },
  {
    id: "ps-9",
    name: "이름 없는 공터",
    category: "ETC",
    address: "서울 성동구 성수동 · 제보된 좌표",
    region: "성수동",
    lat: 37.541,
    lng: 127.052,
    description: "공사 가림막 옆 빈 땅.",
    imageUrl: null,
    submitterId: "u-haeun",
    submittedAt: ago(4),
    status: "REJECTED",
    reviewedAt: ago(3),
    rejectReason: "장소 이름과 위치가 특정되지 않아요.",
    placeId: null,
  },
  {
    id: "ps-10",
    name: "폐업한 분식집",
    category: "RESTAURANT",
    address: "서울 마포구 망원동 · 제보된 좌표",
    region: "망원동",
    lat: 37.5569,
    lng: 126.9055,
    description: "간판만 남아 있는 가게.",
    imageUrl: null,
    submitterId: "u-yujin",
    submittedAt: ago(10),
    status: "REJECTED",
    reviewedAt: ago(8),
    rejectReason: "현재 영업하지 않는 장소예요.",
    placeId: null,
  },
];
