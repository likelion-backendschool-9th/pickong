export interface User {
  id: string;
  /** 닉네임 = 사용자 ID (영문, 중복 불가). 프로필 주소에 쓰입니다. */
  username: string;
  /** 화면에 보여 줄 이름: 이름이 있으면 이름, 없으면 닉네임(username) */
  nickname: string;
  /** 사용자가 정한 이름(중복 가능). 정하지 않았으면 null */
  name: string | null;
  profileImageUrl: string | null;
  bio: string;
  region: string;
  postCount: number;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isMe: boolean;
  /** 유료 컬렉션을 판매할 수 있는 크리에이터 */
  isCreator: boolean;
  /** 내 정보에만 있음: 닉네임을 다시 바꿀 수 있는 시각(ISO). 지금 바꿀 수 있거나 다른 사람이면 null */
  usernameChangeableAt: string | null;
}

/** 다른 엔티티에 포함되는 작성자·소유자 요약 */
export interface UserSummary {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}
