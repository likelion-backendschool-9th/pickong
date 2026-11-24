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
}

/** 다른 엔티티에 포함되는 작성자·소유자 요약 */
export interface UserSummary {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}
