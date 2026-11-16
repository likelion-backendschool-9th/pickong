/**
 * Mock 서버의 메모리 저장소와 Row → DTO 변환.
 * 서버를 다시 시작하면 fixtures 상태로 초기화됩니다.
 */
import { collections, savedPlaces } from "./fixtures/collections.ts";
import { conversations, messages } from "./fixtures/messages.ts";
import { places } from "./fixtures/places.ts";
import { notifications, plans } from "./fixtures/plans.ts";
import { comments, posts } from "./fixtures/posts.ts";
import type {
  CollectionRow,
  PlaceRow,
  PlanRow,
  PostRow,
  SavedPlaceRow,
  UserRow,
} from "./fixtures/types.ts";
import { follows, ME_ID, users } from "./fixtures/users.ts";

const clone = <T>(value: T): T => structuredClone(value);

export const db = {
  users: clone(users),
  follows: clone(follows),
  places: clone(places),
  posts: clone(posts),
  comments: clone(comments),
  savedPlaces: clone(savedPlaces),
  collections: clone(collections),
  plans: clone(plans),
  notifications: clone(notifications),
  conversations: clone(conversations),
  messages: clone(messages),
};

export { ME_ID };

let seq = 1000;
export const nextId = (prefix: string) => `${prefix}-${++seq}`;

export const findUser = (id: string) => db.users.find((u) => u.id === id);
export const findUserByName = (username: string) => db.users.find((u) => u.username === username);
export const findPlace = (id: string) => db.places.find((p) => p.id === id);

export const isFollowing = (follower: string, following: string) =>
  db.follows.some(([a, b]) => a === follower && b === following);

/** 보는 사람 기준으로 볼 수 있는지 (공개 / 팔로워 공개 / 비공개) */
export function canView(ownerId: string, visibility: string, viewerId = ME_ID) {
  if (ownerId === viewerId) return true;
  if (visibility === "PUBLIC") return true;
  if (visibility === "FOLLOWERS") return isFollowing(viewerId, ownerId);
  return false;
}

export function userDto(u: UserRow) {
  return {
    id: u.id,
    username: u.username,
    nickname: u.nickname,
    profileImageUrl: u.profileImageUrl,
    bio: u.bio,
    region: u.region,
    // 목록과 숫자가 어긋나지 않도록 실제 데이터에서 셉니다.
    postCount: db.posts.filter((p) => p.authorId === u.id && canView(p.authorId, p.visibility)).length,
    followerCount: db.follows.filter(([, b]) => b === u.id).length,
    followingCount: db.follows.filter(([a]) => a === u.id).length,
    isFollowing: isFollowing(ME_ID, u.id),
    isMe: u.id === ME_ID,
  };
}

export function placeDto(p: PlaceRow) {
  return {
    id: p.id,
    name: p.name,
    address: p.address,
    region: p.region,
    lat: p.lat,
    lng: p.lng,
    category: p.category,
    isCustom: p.isCustom,
    visibility: p.visibility,
    description: p.description,
    imageUrl: p.imageUrl,
    businessHours: p.businessHours,
  };
}

export function postDto(p: PostRow) {
  const author = findUser(p.authorId)!;
  const placeIds = [...new Set(p.photos.map((ph) => ph.placeId).filter((id): id is string => !!id))];
  return {
    id: p.id,
    author: userDto(author),
    photos: p.photos.map((ph) => ({ url: ph.url, placeId: ph.placeId })),
    places: placeIds.map((id) => placeDto(findPlace(id)!)),
    content: p.content,
    visibility: p.visibility,
    likeCount: p.likeCount,
    commentCount: db.comments.filter((c) => c.postId === p.id).length,
    isLiked: p.likedByMe,
    isBookmarked: p.bookmarkedByMe,
    createdAt: p.createdAt,
  };
}

export function savedPlaceDto(s: SavedPlaceRow) {
  return {
    id: s.id,
    place: placeDto(findPlace(s.placeId)!),
    collectionIds: s.collectionIds,
    visitStatus: s.visitStatus,
    memo: s.memo,
    savedAt: s.savedAt,
    source: s.source,
  };
}

export function collectionPlaceIds(c: CollectionRow) {
  return db.savedPlaces
    .filter((s) => s.ownerId === c.ownerId && s.collectionIds.includes(c.id))
    .map((s) => s.placeId);
}

export function collectionDto(c: CollectionRow) {
  const owner = findUser(c.ownerId)!;
  const placeIds = collectionPlaceIds(c);
  const firstImage = placeIds.map((id) => findPlace(id)?.imageUrl).find(Boolean) ?? null;
  return {
    id: c.id,
    owner: { id: owner.id, username: owner.username, nickname: owner.nickname, profileImageUrl: owner.profileImageUrl },
    title: c.title,
    description: c.description,
    coverImageUrl: c.coverImageUrl ?? firstImage,
    visibility: c.visibility,
    pinColor: c.pinColor,
    placeCount: placeIds.length,
    isMine: c.ownerId === ME_ID,
  };
}

export function planDto(p: PlanRow) {
  return {
    id: p.id,
    title: p.title,
    date: p.date,
    region: p.region,
    startPlace: placeDto(findPlace(p.startPlaceId)!),
    startTime: p.startTime,
    endTime: p.endTime,
    memo: p.memo,
    status: p.status,
    items: p.items.map((it) => ({
      id: it.id,
      kind: it.kind,
      place: placeDto(findPlace(it.placeId)!),
      stayMinutes: it.stayMinutes,
      visited: it.visited,
      reservation: it.reservation,
    })),
  };
}

/** 두 좌표 사이 거리 (m) */
export function distanceMeters(a: { lat: number; lng: number }, b: { lat: number; lng: number }) {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
