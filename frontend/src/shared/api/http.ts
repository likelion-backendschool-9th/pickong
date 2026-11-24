import { ApiError, SESSION_EXPIRED } from "./errors";

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  query?: Record<string, string | number | undefined | null>;
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * 브라우저가 요청하는 API 경로 앞부분. next.config.ts의 rewrites가 백엔드 또는 mock으로 전달합니다.
 * API 요청뿐 아니라 소셜 로그인 시작 주소처럼 페이지 이동으로 여는 API 주소도 이 값으로 만듭니다.
 */
export const API_BASE = "/api/v1";

/** 인증 경로(재발급 · 로그아웃 · 온보딩 등)는 401이 와도 재발급을 시도하지 않고 그대로 오류를 돌려줍니다. */
const AUTH_PATH_PREFIX = "/auth/";

/**
 * CSRF 이중 제출: 서버가 내려준 XSRF-TOKEN 쿠키 값을 쓰기 요청의 X-XSRF-TOKEN 헤더로 보냅니다.
 * 다른 사이트는 이 쿠키를 읽을 수 없어 헤더를 만들 수 없습니다. 이름은 Spring Security 기본값입니다.
 */
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
/** CSRF 헤더가 없거나 맞지 않을 때 서버가 보내는 403 code */
const INVALID_CSRF_TOKEN = "INVALID_CSRF_TOKEN";

/** 여러 탭이 같은 Refresh Token으로 동시에 재발급하지 않도록 거는 브라우저 잠금(Web Locks) 이름 */
export const REFRESH_LOCK = "pickong-token-refresh";

/** 브라우저 쿠키 하나를 읽습니다. 서버 렌더링 중이거나 쿠키가 없으면 null */
export function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  for (const part of document.cookie.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

/**
 * fetch를 한 번 보냅니다. 쓰기 요청이면 CSRF 헤더를 붙이고, 403 INVALID_CSRF_TOKEN이면 쿠키를 다시 읽어 한 번만 재시도합니다.
 * 처음 쓰기 요청 전에 쿠키가 아직 없거나 바뀐 경우, 서버가 그 403 응답에 새 XSRF-TOKEN 쿠키를 담아 주기 때문에 재시도는 통과합니다.
 */
async function send(method: Method, url: string, init: { body?: string; signal?: AbortSignal } = {}): Promise<Response> {
  const attempt = () => {
    const headers: Record<string, string> = {};
    if (init.body !== undefined) headers["Content-Type"] = "application/json";
    const csrf = method === "GET" ? null : readCookie(CSRF_COOKIE);
    if (csrf) headers[CSRF_HEADER] = csrf;
    return fetch(url, { method, headers, body: init.body, signal: init.signal, credentials: "include" });
  };

  const response = await attempt();
  if (response.status !== 403 || method === "GET") return response;
  const data = await response.clone().json().catch(() => null);
  return data?.code === INVALID_CSRF_TOKEN ? attempt() : response;
}

/**
 * 같은 브라우저의 다른 탭과 재발급이 겹치지 않도록 잠금 안에서 실행합니다.
 * Refresh Token은 쓸 때마다 바뀌고(로테이션) 서버는 유예 시간 없이 재사용을 막으므로,
 * 두 탭이 같은 토큰으로 동시에 재발급하면 뒤에 온 요청이 재사용으로 판단되어 세션 전체가 폐기됩니다.
 * 줄을 서면 뒤 탭은 앞 탭이 받아 둔 새 Refresh Token 쿠키로 요청하므로 문제가 없습니다.
 * Web Locks를 지원하지 않는 환경(테스트 · 아주 오래된 브라우저)에서는 잠금 없이 실행합니다.
 */
async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
  // locks.request는 task가 돌려준 Promise를 한 번 더 감싸서 돌려주므로 await로 풉니다.
  return locks ? await locks.request(REFRESH_LOCK, task) : task();
}

let refreshing: Promise<boolean> | null = null;

/**
 * Refresh Token(httpOnly 쿠키)으로 Access Token을 다시 받습니다.
 * - 한 탭 안: 동시에 여러 요청이 401을 받아도 재발급 요청은 하나만 보냅니다.
 * - 여러 탭 사이: Web Locks로 한 번에 한 탭만 재발급합니다.
 */
export function refreshSession(): Promise<boolean> {
  refreshing ??= withRefreshLock(() => send("POST", `${API_BASE}/auth/refresh`))
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/**
 * fetch 래퍼. 모든 API 호출은 이 함수를 거칩니다.
 * 브라우저에서는 같은 출처의 /api/v1/*로 요청하고, next.config.ts의 rewrites가 백엔드 또는 mock으로 전달합니다.
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
    response = await send(method, url, {
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (cause) {
    if ((cause as Error).name === "AbortError") throw cause;
    throw new ApiError(0, "NETWORK", "서버에 연결할 수 없어요. 백엔드나 mock 서버가 실행 중인지 확인해 주세요.");
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
