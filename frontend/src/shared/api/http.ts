import { ApiError, SESSION_EXPIRED } from "./errors";

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  query?: Record<string, string | number | undefined | null>;
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * 브라우저가 요청하는 API 경로 앞부분. next.config.ts의 rewrites가 백엔드(또는 mock)로 전달합니다.
 * API 요청뿐 아니라 소셜 로그인 시작 주소처럼 페이지 이동으로 여는 API 주소도 이 값으로 만듭니다.
 */
export const API_BASE = "/api";

/** 인증 경로(재발급 · 로그아웃 · 온보딩 등)는 401이 와도 재발급을 시도하지 않고 그대로 오류를 돌려줍니다. */
const AUTH_PATH_PREFIX = "/auth/";

let refreshing: Promise<boolean> | null = null;

/**
 * Refresh Token(httpOnly 쿠키)으로 Access Token을 다시 받습니다.
 * 동시에 여러 요청이 401을 받아도 재발급 요청은 하나만 보냅니다.
 * Refresh Token은 쓸 때마다 바뀌므로(로테이션), 같은 토큰으로 두 번 요청하면 재사용으로 판단되어 세션 전체가 폐기됩니다.
 */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(`${API_BASE}/auth/refresh`, { method: "POST", credentials: "include" })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/**
 * fetch 래퍼. 모든 API 호출은 이 함수를 거칩니다.
 * 브라우저에서는 같은 출처의 /api/*로 요청하고, next.config.ts의 rewrites가 백엔드(또는 mock)로 전달합니다.
 * 401을 받으면 Access Token을 한 번 재발급하고 다시 요청합니다. 재발급도 실패하면 SESSION_EXPIRED 오류를 던집니다.
 */
async function request<T>(method: Method, path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const url = `${API_BASE}${path}${search.size ? `?${search}` : ""}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: options.body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
      credentials: "include",
    });
  } catch (cause) {
    if ((cause as Error).name === "AbortError") throw cause;
    throw new ApiError(0, "NETWORK", "서버에 연결할 수 없어요. mock 서버가 실행 중인지 확인해 주세요.");
  }

  if (response.status === 401 && !path.startsWith(AUTH_PATH_PREFIX)) {
    if (!retried && (await refreshSession())) return request<T>(method, path, options, true);
    throw new ApiError(401, SESSION_EXPIRED, "로그인이 필요해요. 다시 로그인해 주세요.");
  }

  if (response.status === 204) return undefined as T;

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.code ?? "UNKNOWN",
      data?.message ?? "요청을 처리하지 못했어요.",
    );
  }
  return data as T;
}

export const http = {
  get: <T>(path: string, options?: RequestOptions) => request<T>("GET", path, options),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, { body }),
  delete: <T>(path: string, body?: unknown) => request<T>("DELETE", path, { body }),
};
