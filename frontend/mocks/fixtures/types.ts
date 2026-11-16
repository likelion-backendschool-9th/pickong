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
  username: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string;
  region: string;
  postCount: number;
  followerCount: number;
  followingCount: number;
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

export interface PlanItemRow {
  id: string;
  kind: "RESERVATION" | "FREE";
  placeId: string;
  stayMinutes: number;
  visited: boolean;
  reservation: ReservationDto | null;
}

export interface PlanRow {
  id: string;
  ownerId: string;
  title: string;
  date: string;
  region: string;
  startPlaceId: string;
  startTime: string;
  endTime: string;
  memo: string;
  status: "UPCOMING" | "DONE";
  items: PlanItemRow[];
}

export interface NotificationRow {
  id: string;
  type: "FOLLOW" | "COMMENT" | "LIKE" | "COLLECTION_SAVE" | "PLAN_CONFLICT";
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
