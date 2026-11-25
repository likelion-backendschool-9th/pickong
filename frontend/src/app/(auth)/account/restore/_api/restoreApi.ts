import { useMutation, useQuery } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

/** 탈퇴 취소 쿠키(10분)의 탈퇴 상태. anonymizeAt이 지나면 계정 정보가 지워지고 취소할 수 없습니다. */
export interface RestoreSession {
  username: string;
  requestedAt: string;
  anonymizeAt: string;
}

/**
 * 탈퇴 상태. 쿠키가 없거나 만료면 401 RESTORE_SESSION_EXPIRED, 30일이 지났으면 409 WITHDRAWAL_NOT_CANCELLABLE.
 * (/auth 아래라 401이어도 토큰 재발급을 시도하지 않고 그대로 오류가 옵니다)
 */
export const useRestoreSession = () =>
  useQuery({ queryKey: ["auth", "withdrawal"], queryFn: () => http.get<RestoreSession>("/auth/withdrawal") });

/** 탈퇴 취소: 성공하면 서버가 ACTIVE로 돌리고 로그인 세션 쿠키를 줍니다. */
export const useCancelWithdrawal = () =>
  useMutation({ mutationFn: () => http.post<{ username: string }>("/auth/withdrawal/cancel") });

/** 탈퇴 유지: 탈퇴 취소 쿠키만 지웁니다. */
export const useLeaveRestore = () => useMutation({ mutationFn: () => http.delete<void>("/auth/withdrawal") });
