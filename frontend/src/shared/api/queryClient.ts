import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { routes } from "@/shared/config/routes";
import { ApiError, SESSION_EXPIRED, TERMS_RECONSENT_REQUIRED } from "./errors";

/**
 * 공통 오류에 따라 보낼 화면. 보내지 않으면 null.
 * - 재발급까지 실패(로그아웃 · 토큰 폐기 · 만료) → 로그인 화면
 * - 바뀐 필수 약관에 아직 동의하지 않음 → 재동의 화면. 동의한 뒤 돌아오도록 지금 주소를 next로 넘깁니다.
 *   이미 재동의 화면이면 보내지 않습니다(같은 화면을 계속 다시 불러오지 않게).
 */
export function redirectTargetFor(error: unknown, location: { pathname: string; search: string }): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.code === SESSION_EXPIRED) return routes.login(SESSION_EXPIRED);
  if (error.code === TERMS_RECONSENT_REQUIRED) {
    const consentPath = routes.termsConsent();
    return location.pathname === consentPath ? null : routes.termsConsent(location.pathname + location.search);
  }
  return null;
}

/** 화면을 새로 불러와 남은 캐시(막혀서 실패한 요청 등)도 비웁니다. */
function redirectOnCommonError(error: unknown) {
  if (typeof window === "undefined") return;
  const target = redirectTargetFor(error, window.location);
  if (target) window.location.assign(target);
}

export function makeQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({ onError: redirectOnCommonError }),
    mutationCache: new MutationCache({ onError: redirectOnCommonError }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
    },
  });
}
