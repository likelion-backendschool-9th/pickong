import { http } from "@/shared/api/http";

/**
 * 인증 API. 토큰은 서버가 httpOnly 쿠키로 내려주므로 프론트는 토큰을 다루지 않습니다.
 * 로그인 · 회원가입은 소셜 로그인(카카오 · Google)으로만 하며, 시작 주소는 socialProviders.ts의 oauthAuthorizeUrl입니다.
 * Access Token 재발급(`POST /auth/refresh`)은 http.ts가 401을 받았을 때 알아서 처리합니다.
 */
export const authApi = {
  /** 서버에 저장된 Refresh Token을 폐기하고 인증 쿠키를 지웁니다. */
  logout: () => http.post<void>("/auth/logout"),
};
