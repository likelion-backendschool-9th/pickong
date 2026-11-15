import { toPlace } from "@/entities/place";
import { toUser } from "@/entities/user";
import type { FeedPlace, Post, PostComment } from "../model";
import type { CommentDto, FeedPlaceDto, PostDto } from "./dto";

export const toPost = (dto: PostDto): Post => ({
  id: dto.id,
  author: toUser(dto.author),
  photos: dto.photos.map((p) => ({ url: p.url, placeId: p.placeId })),
  places: dto.places.map(toPlace),
  content: dto.content,
  visibility: dto.visibility,
  likeCount: dto.likeCount,
  commentCount: dto.commentCount,
  isLiked: dto.isLiked,
  isBookmarked: dto.isBookmarked,
  createdAt: dto.createdAt,
});

export const toComment = (dto: CommentDto): PostComment => ({
  id: dto.id,
  postId: dto.postId,
  parentId: dto.parentId,
  author: toUser(dto.author),
  content: dto.content,
  createdAt: dto.createdAt,
});

export const toFeedPlace = (dto: FeedPlaceDto): FeedPlace => ({
  place: toPlace(dto.place),
  postIds: dto.postIds,
  postCount: dto.postCount,
});
