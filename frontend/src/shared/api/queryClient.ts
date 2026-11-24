import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { routes } from "@/shared/config/routes";
import { ApiError, SESSION_EXPIRED } from "./errors";

/** 재발급까지 실패하면(로그아웃 · 토큰 폐기 · 만료) 로그인 화면으로 보냅니다. 화면을 새로 불러와 남은 캐시도 비웁니다. */
function redirectToLoginIfSessionExpired(error: unknown) {
  if (typeof window === "undefined") return;
  if (error instanceof ApiError && error.code === SESSION_EXPIRED) {
    window.location.assign(routes.login(SESSION_EXPIRED));
  }
}

export function makeQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({ onError: redirectToLoginIfSessionExpired }),
    mutationCache: new MutationCache({ onError: redirectToLoginIfSessionExpired }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
    },
  });
}
