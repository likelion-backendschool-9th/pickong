export interface User {
  id: string;
  username: string;
  nickname: string;
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
