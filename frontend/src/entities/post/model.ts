import type { Visibility } from "@/shared/config/visibility";
import type { Place } from "@/entities/place";
import type { User } from "@/entities/user";

/** 게시물의 사진 한 장. 사진마다 장소를 하나씩 연결할 수 있습니다 (없을 수도 있음). */
export interface PostPhoto {
  url: string;
  placeId: string | null;
}

export interface Post {
  id: string;
  author: User;
  photos: PostPhoto[];
  /** 사진들에 연결된 장소 (중복 제거, 사진 순서) */
  places: Place[];
  content: string;
  visibility: Visibility;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  isBookmarked: boolean;
  createdAt: string;
}

export interface PostComment {
  id: string;
  postId: string;
  parentId: string | null;
  author: User;
  content: string | null;
  // 소프트 삭제 여부 — true면 대댓글이 남아있어 "삭제된 댓글입니다"로 표시
  deleted: boolean;
  createdAt: string;
}

export interface FeedPlace {
  place: Place;
  postIds: string[];
  postCount: number;
}

/** index번째 사진에 연결된 장소. 없으면 null */
export function placeOfPhoto(post: Post, index: number): Place | null {
  const placeId = post.photos[index]?.placeId;
  return placeId ? (post.places.find((p) => p.id === placeId) ?? null) : null;
}
