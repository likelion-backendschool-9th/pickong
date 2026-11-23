export interface UserDto {
  id: string;
  username: string;
  /** 표시 이름 = 이름 ?? 닉네임(username). 서버가 계산해서 보냅니다. */
  nickname: string;
  /** 사용자가 정한 이름(display_name). 없으면 null */
  name?: string | null;
  profileImageUrl: string | null;
  bio: string | null;
  region: string | null;
  postCount: number;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isMe: boolean;
  isCreator: boolean;
}

export interface UserSummaryDto {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}
