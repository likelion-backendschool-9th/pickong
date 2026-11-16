import type { NotificationRow, PlanRow } from "./types.ts";

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

export const plans: PlanRow[] = [
  {
    id: "plan-1",
    ownerId: "u-seoyeon",
    title: "성수에서 보내는 토요일",
    date: "2026-11-21",
    region: "성수동",
    memo: "친구 1명 · 많이 걷지 않는 동선",
    status: "PLANNING",
    items: [
      { id: "pi-1", placeId: "p-salt", fixedTime: null, visited: false },
      { id: "pi-2", placeId: "p-yeonmu", fixedTime: null, visited: false },
      { id: "pi-3", placeId: "p-rest-a", fixedTime: "12:30", visited: false },
      { id: "pi-4", placeId: "p-onyu", fixedTime: null, visited: false },
      { id: "pi-5", placeId: "p-gyeol", fixedTime: null, visited: false },
      { id: "pi-6", placeId: "p-cinema", fixedTime: "17:00", visited: false },
    ],
  },
  {
    id: "plan-2",
    ownerId: "u-seoyeon",
    title: "서울숲 가을 산책",
    date: "2026-11-07",
    region: "서울숲",
    memo: "",
    status: "DONE",
    items: [
      { id: "pi-21", placeId: "p-forest", fixedTime: null, visited: true },
      { id: "pi-22", placeId: "p-book", fixedTime: null, visited: true },
      { id: "pi-23", placeId: "p-onyu", fixedTime: null, visited: true },
      { id: "pi-24", placeId: "p-yeonmu", fixedTime: null, visited: false },
    ],
  },
];

export const notifications: NotificationRow[] = [
  {
    id: "n-1",
    type: "FOLLOW",
    actorId: "u-jimin",
    message: "지민님이 회원님을 팔로우했어요.",
    detail: "",
    link: "/users/jimin",
    createdAt: ago(12),
    read: false,
  },
  {
    id: "n-2",
    type: "COMMENT",
    actorId: "u-minsu",
    message: "민수님이 게시물에 댓글을 남겼어요.",
    detail: "“이 카페 진짜 좋아요!”",
    link: "/posts/post-5",
    createdAt: ago(45),
    read: false,
  },
  {
    id: "n-3",
    type: "COLLECTION_SAVE",
    actorId: "u-haeun",
    message: "하은님이 ‘서울 사진 명소’ 컬렉션의 장소를 저장했어요.",
    detail: "",
    link: "/collections/c-photo",
    createdAt: ago(60 * 3),
    read: true,
  },
  {
    id: "n-4",
    type: "PLAN_REMINDER",
    actorId: null,
    message: "토요일 일정이 아직 계획 중이에요.",
    detail: "성수에서 보내는 토요일 · 순서를 정하고 ‘계획 완료’를 눌러 보세요",
    link: "/plans/plan-1",
    createdAt: ago(60 * 5),
    read: true,
  },
];
