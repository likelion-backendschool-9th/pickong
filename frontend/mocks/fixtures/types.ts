/**
 * Mock 서버가 응답하는 DTO 형태.
 * 실제 백엔드(Spring Boot) 계약이 정해지면 이 형태를 계약에 맞추고,
 * 프론트 쪽 차이는 src/entities/<엔티티>/api/mapper.ts에서 흡수합니다.
 */
export type Visibility = "PUBLIC" | "FOLLOWERS" | "PRIVATE";

export type PlaceCategory =
  | "CAFE"
  | "RESTAURANT"
  | "PHOTO_SPOT"
  | "SHOP"
  | "EXHIBITION"
  | "STREET"
  | "PARK"
  | "ATTRACTION"
  | "STATION"
  | "ETC";

export interface UserRow {
  id: string;
  /** 닉네임 = 사용자 ID */
  username: string;
  /** 화면에 보여 줄 이름 = 이름 ?? username. 이름을 바꾸면 users.ts가 다시 계산합니다. */
  nickname: string;
  /** 사용자가 정한 이름(중복 가능). undefined(기존 fixture)면 nickname을 이름으로 봅니다. null이면 이름 없음 */
  name?: string | null;
  profileImageUrl: string | null;
  bio: string;
  region: string;
  postCount: number;
  followerCount: number;
  followingCount: number;
  /** 유료 컬렉션을 판매할 수 있는 크리에이터 */
  isCreator?: boolean;
  /** 마지막으로 닉네임(username)을 바꾼 시각(ISO). 없으면 아직 바꾼 적 없음 → 바로 바꿀 수 있음 */
  usernameChangedAt?: string;
}

export interface BusinessHoursDto {
  open: string; // HH:mm
  close: string; // HH:mm (24:00 = 자정)
  closedDays: number[]; // 0=일 ~ 6=토
}

export interface PlaceRow {
  id: string;
  name: string;
  address: string;
  region: string;
  lat: number;
  lng: number;
  category: PlaceCategory;
  isCustom: boolean;
  ownerId: string | null;
  visibility: Visibility;
  description: string;
  imageUrl: string | null;
  businessHours: BusinessHoursDto | null;
}

export interface PostRow {
  id: string;
  authorId: string;
  photos: { url: string; placeId: string | null }[];
  content: string;
  visibility: Visibility;
  likeCount: number;
  likedByMe: boolean;
  bookmarkedByMe: boolean;
  createdAt: string;
}

export interface CommentRow {
  id: string;
  postId: string;
  authorId: string;
  parentId: string | null;
  content: string;
  createdAt: string;
}

export type VisitStatus = "WANT_TO_GO" | "VISITED";

export interface SavedPlaceRow {
  id: string;
  ownerId: string;
  placeId: string;
  collectionIds: string[];
  visitStatus: VisitStatus;
  memo: string;
  savedAt: string;
  source: { type: "POST" | "COLLECTION" | "LINK_IMPORT" | "SEARCH" | "CUSTOM"; refId: string | null; label: string };
}

export interface CollectionRow {
  id: string;
  ownerId: string;
  title: string;
  description: string;
  coverImageUrl: string | null;
  visibility: Visibility;
  pinColor: "LIME" | "GREEN" | "BROWN";
  createdAt: string;
  /** 유료 컬렉션 가격(원). 없으면 무료. 크리에이터의 전체 공개 컬렉션만 유료로 설정할 수 있습니다. */
  price?: number | null;
}

/** 유료 컬렉션 구매 기록 (단건 구매 · 한 번 사면 계속 열람) */
export interface PurchaseRow {
  id: string;
  userId: string;
  collectionId: string;
  amount: number;
  /** TEST: 개발용 테스트 결제 / CARD: 카드 결제 (지금은 mock PG 승인) */
  method: "TEST" | "CARD";
  /** 카드 결제일 때 카드사 · 할부 개월 (0 = 일시불) */
  card?: { company: string; installment: number } | null;
  purchasedAt: string;
  status?: "PAID" | "REFUND_REQUESTED" | "REFUNDED" | "REFUND_REJECTED";
  refund?: { reason: string; detail: string; requestedAt: string; processedAt: string | null; rejectReason: string | null } | null;
}

export interface ReservationDto {
  type: "RESTAURANT" | "SHOW" | "EXHIBITION" | "STAY" | "ETC";
  startTime: string;
  endTime: string;
  people: number;
  reservationNumber: string;
  link: string;
  memo: string;
}

/** 일정 장소: 시간이 정해진 곳(영화 · 식당 예약 등)만 fixedTime(HH:mm) */
export interface PlanItemRow {
  id: string;
  placeId: string;
  fixedTime: string | null;
  visited: boolean;
}

/** PLANNING: 계획 중 / UPCOMING: 계획 완료 / DONE: 방문 기록 완료 */
export interface PlanRow {
  id: string;
  ownerId: string;
  title: string;
  date: string;
  region: string;
  memo: string;
  status: "PLANNING" | "UPCOMING" | "DONE";
  items: PlanItemRow[];
}

export interface NotificationRow {
  id: string;
  type: "FOLLOW" | "COMMENT" | "LIKE" | "COLLECTION_SAVE" | "PLAN_REMINDER";
  actorId: string | null;
  message: string;
  detail: string;
  link: string;
  createdAt: string;
  read: boolean;
}

/** DM 대화방 (1:1). lastReadAt: 사용자별로 마지막으로 읽은 시각 */
export interface ConversationRow {
  id: string;
  memberIds: [string, string];
  lastReadAt: Record<string, string>;
}

export type MessageAttachmentRow =
  | { type: "POST"; refId: string }
  | { type: "PLACE"; refId: string }
  /** 사진: 업로드 API 전까지는 브라우저 임시 URL (새로고침하면 사라짐) */
  | { type: "IMAGE"; url: string };

export interface MessageRow {
  id: string;
  conversationId: string;
  senderId: string;
  text: string;
  attachment: MessageAttachmentRow | null;
  createdAt: string;
}
