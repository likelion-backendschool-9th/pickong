import { http } from "@/shared/api/http";

export interface LoginInput {
  /** 이메일 또는 닉네임. @가 있으면 이메일로 봅니다. (닉네임에는 @를 쓸 수 없음) */
  loginId: string;
  password: string;
  keepSignedIn: boolean;
}

/** USER: 일반 회원 / ADMIN: 회원과 별개인 관리자 계정 (common_blueprint.md 5.3) */
export type AccountRole = "USER" | "ADMIN";

/**
 * 인증 API. 토큰은 서버가 httpOnly 쿠키로 내려주므로 프론트는 토큰을 다루지 않습니다.
 */
export const authApi = {
  login: (input: LoginInput) => http.post<{ role: AccountRole; nickname: string }>("/auth/login", input),
  logout: () => http.post<void>("/auth/logout"),
};
