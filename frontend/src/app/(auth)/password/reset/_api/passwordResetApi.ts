import { http } from "@/shared/api/http";

/** 비밀번호 재설정: 이메일로 인증번호 받기 → 인증 → 새 비밀번호 저장 */
export const passwordResetApi = {
  request: (email: string) => http.post<{ requestId: string; sentTo: string }>("/auth/password-reset", { email }),
  verify: (requestId: string, code: string) => http.post<void>(`/auth/password-reset/${requestId}/verify`, { code }),
  confirm: (requestId: string, password: string) => http.post<void>(`/auth/password-reset/${requestId}/confirm`, { password }),
};
