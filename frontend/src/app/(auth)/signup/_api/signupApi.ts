import { useQuery } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

/**
 * 닉네임 중복 확인. 닉네임은 로그인 아이디로도 쓰이므로 중복될 수 없습니다.
 * 백엔드 구현 전이라 지금은 mock(`GET /api/auth/nickname-availability`)만 응답합니다.
 */
export const useNicknameAvailability = (nickname: string, enabled: boolean) =>
  useQuery({
    queryKey: ["auth", "nickname-availability", nickname],
    queryFn: ({ signal }) => http.get<{ available: boolean }>("/auth/nickname-availability", { query: { nickname }, signal }),
    enabled,
    staleTime: 30_000,
    // 입력칸 옆 표시가 오래 돌지 않도록 실패하면 바로 알려 줍니다.
    retry: false,
  });
