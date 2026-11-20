import { http } from "@/shared/api/http";

export interface LoginInput {
  email: string;
  password: string;
  keepSignedIn: boolean;
}

/**
 * 인증 API. 토큰은 서버가 httpOnly 쿠키로 내려주므로 응답 본문은 쓰지 않습니다.
 * (common_blueprint.md 5.3)
 */
export const authApi = {
  login: (input: LoginInput) => http.post<unknown>("/auth/login", input).then(() => undefined),
};
