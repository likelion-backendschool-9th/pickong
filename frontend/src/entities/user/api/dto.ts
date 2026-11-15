export interface UserDto {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
  region: string | null;
  postCount: number;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isMe: boolean;
}

export interface UserSummaryDto {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}
