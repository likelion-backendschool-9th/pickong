import type { User, UserSummary } from "../model";
import type { UserDto, UserSummaryDto } from "./dto";

export const toUser = (dto: UserDto): User => ({
  id: dto.id,
  username: dto.username,
  nickname: dto.nickname,
  name: dto.name ?? null,
  profileImageUrl: dto.profileImageUrl,
  bio: dto.bio ?? "",
  region: dto.region ?? "",
  postCount: dto.postCount,
  followerCount: dto.followerCount,
  followingCount: dto.followingCount,
  isFollowing: dto.isFollowing,
  isMe: dto.isMe,
  isCreator: dto.isCreator ?? false,
  usernameChangeableAt: dto.usernameChangeableAt ?? null,
});

export const toUserSummary = (dto: UserSummaryDto): UserSummary => ({
  id: dto.id,
  username: dto.username,
  nickname: dto.nickname,
  profileImageUrl: dto.profileImageUrl,
});
