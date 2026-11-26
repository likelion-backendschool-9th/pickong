import type { Visibility } from "@/shared/config/visibility";
import type { PlaceDto } from "@/entities/place";
import type { UserDto } from "@/entities/user";

export interface PostDto {
  id: string;
  author: UserDto;
  photos: { url: string; placeId: string | null }[];
  places: PlaceDto[];
  content: string;
  visibility: Visibility;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  isBookmarked: boolean;
  createdAt: string;
}

export interface CommentDto {
  id: string;
  postId: string;
  parentId: string | null;
  author: UserDto;
  content: string | null;
  // 소프트 삭제된 댓글 여부 — true면 대댓글이 남아있어 목록에 보이는 상태
  deleted: boolean;
  createdAt: string;
}

export interface FeedPlaceDto {
  place: PlaceDto;
  postIds: string[];
  postCount: number;
}
