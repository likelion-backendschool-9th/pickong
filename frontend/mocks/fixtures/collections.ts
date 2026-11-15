import type { CollectionRow, SavedPlaceRow } from "./types.ts";

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

export const collections: CollectionRow[] = [
  {
    id: "c-cafe",
    ownerId: "u-seoyeon",
    title: "성수 카페",
    description: "햇살 좋은 자리, 오래 머물고 싶은 카페.",
    coverImageUrl: "/images/photos/cafe.jpg",
    visibility: "PRIVATE",
    pinColor: "LIME",
    createdAt: ago(40),
  },
  {
    id: "c-photo",
    ownerId: "u-seoyeon",
    title: "서울 사진 명소",
    description: "사진 찍기 좋은 골목과 공원.",
    coverImageUrl: "/images/photos/park.jpg",
    visibility: "PUBLIC",
    pinColor: "GREEN",
    createdAt: ago(30),
  },
  {
    id: "c-weekend",
    ownerId: "u-seoyeon",
    title: "주말에 가볼 곳",
    description: "",
    coverImageUrl: "/images/photos/shop.jpg",
    visibility: "PRIVATE",
    pinColor: "BROWN",
    createdAt: ago(10),
  },
  {
    id: "c-jimin-seongsu",
    ownerId: "u-jimin",
    title: "성수 카페",
    description: "햇살 좋은 자리, 오래 머물고 싶은 공간.",
    coverImageUrl: "/images/photos/cafe.jpg",
    visibility: "PUBLIC",
    pinColor: "LIME",
    createdAt: ago(20),
  },
  {
    id: "c-jimin-photo",
    ownerId: "u-jimin",
    title: "서울 사진 명소",
    description: "",
    coverImageUrl: "/images/photos/park.jpg",
    visibility: "PUBLIC",
    pinColor: "GREEN",
    createdAt: ago(18),
  },
];

const saved = (
  id: string,
  ownerId: string,
  placeId: string,
  collectionIds: string[],
  extra: Partial<SavedPlaceRow> = {},
): SavedPlaceRow => ({
  id,
  ownerId,
  placeId,
  collectionIds,
  visitStatus: "WANT_TO_GO",
  memo: "",
  savedAt: ago(Number(id.replace(/\D/g, "")) || 1),
  source: { type: "POST", refId: "post-3", label: "지인 피드에서 발견" },
  ...extra,
});

export const savedPlaces: SavedPlaceRow[] = [
  saved("sp-1", "u-seoyeon", "p-onyu", ["c-cafe"], {
    source: { type: "POST", refId: "post-1", label: "지민님의 게시물에서 발견" },
  }),
  saved("sp-2", "u-seoyeon", "p-salt", ["c-cafe"], {
    source: { type: "POST", refId: "post-2", label: "민수님의 게시물에서 발견" },
  }),
  saved("sp-3", "u-seoyeon", "p-gyeol", ["c-weekend"], {
    source: { type: "SEARCH", refId: null, label: "직접 검색해서 저장" },
  }),
  saved("sp-4", "u-seoyeon", "p-yeonmu", ["c-photo", "c-weekend"], {
    visitStatus: "VISITED",
  }),
  saved("sp-5", "u-seoyeon", "p-hanok", ["c-photo"], {
    source: { type: "POST", refId: "post-4", label: "유진님의 게시물에서 발견" },
  }),
  saved("sp-6", "u-seoyeon", "p-rest-a", [], {
    source: { type: "LINK_IMPORT", refId: null, label: "블로그 링크에서 가져옴" },
  }),
  saved("sp-7", "u-seoyeon", "p-cinema", []),
  saved("sp-8", "u-seoyeon", "p-book", ["c-weekend"], { visitStatus: "VISITED" }),
  saved("sp-9", "u-seoyeon", "p-baram", ["c-cafe"], {
    source: { type: "POST", refId: "post-4", label: "유진님의 게시물에서 발견" },
  }),

  saved("sp-101", "u-jimin", "p-onyu", ["c-jimin-seongsu"]),
  saved("sp-102", "u-jimin", "p-momo", ["c-jimin-seongsu"]),
  saved("sp-103", "u-jimin", "p-salt", ["c-jimin-seongsu"]),
  saved("sp-104", "u-jimin", "p-forest", ["c-jimin-photo"]),
  saved("sp-105", "u-jimin", "p-forest-view", ["c-jimin-photo"]),
];
