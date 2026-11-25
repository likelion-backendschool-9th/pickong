export interface UserDto {
  id: string;
  username: string;
  /** 표시 이름 = 이름 ?? 아이디(username). 서버가 계산해서 보냅니다. 회원 화면은 아이디만 보여 주므로 관리자 화면에서만 씁니다. */
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
  /** 내 정보(GET · PATCH /me)에만 있음. 아이디를 다시 바꿀 수 있는 시각(ISO). 지금 바꿀 수 있으면 null */
  usernameChangeableAt?: string | null;
}

export interface UserSummaryDto {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}
