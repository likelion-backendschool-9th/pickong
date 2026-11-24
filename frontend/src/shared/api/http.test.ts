import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, SESSION_EXPIRED } from "./errors";
import { http, REFRESH_LOCK } from "./http";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

interface Call {
  url: string;
  method: string;
  csrf: string | undefined;
}

/** 경로별 응답을 차례로 돌려주는 가짜 fetch. 요청마다 주소 · 메서드 · CSRF 헤더를 기록합니다. */
function stubFetch(responses: Record<string, (() => Response | Promise<Response>)[]>) {
  const calls: Call[] = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const headers = (init?.headers ?? {}) as Record<string, string>;
    calls.push({ url, method: init?.method ?? "GET", csrf: headers["X-XSRF-TOKEN"] });
    const next = responses[url]?.shift();
    if (!next) throw new Error(`예상하지 못한 요청: ${url}`);
    return next();
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

/** 브라우저 쿠키(document.cookie) 흉내. 테스트 중에 값을 바꿀 수 있습니다. */
function stubCookies(cookie: string) {
  const doc = { cookie };
  vi.stubGlobal("document", doc);
  return doc;
}

const urls = (calls: Call[]) => calls.map((c) => c.url);

beforeEach(() => vi.unstubAllGlobals());
afterEach(() => vi.unstubAllGlobals());

describe("http: Access Token 재발급", () => {
  it("401을 받으면 한 번 재발급하고 같은 요청을 다시 보낸다", async () => {
    const calls = stubFetch({
      "/api/v1/me": [() => json(401, { code: "UNAUTHORIZED" }), () => json(200, { username: "seoyeon" })],
      "/api/v1/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await expect(http.get("/me")).resolves.toEqual({ username: "seoyeon" });
    expect(urls(calls)).toEqual(["/api/v1/me", "/api/v1/auth/refresh", "/api/v1/me"]);
  });

  it("동시에 401을 받은 요청들은 재발급 요청 하나를 함께 기다린다", async () => {
    let finishRefresh!: () => void;
    const refreshDone = new Promise<Response>((resolve) => {
      finishRefresh = () => resolve(new Response(null, { status: 204 }));
    });
    const calls = stubFetch({
      "/api/v1/me": [() => json(401, {}), () => json(200, { ok: "me" })],
      "/api/v1/feeds": [() => json(401, {}), () => json(200, { ok: "feeds" })],
      "/api/v1/auth/refresh": [() => refreshDone],
    });

    const both = Promise.all([http.get("/me"), http.get("/feeds")]);
    await vi.waitFor(() => expect(urls(calls).filter((u) => u === "/api/v1/auth/refresh")).toHaveLength(1));
    finishRefresh();

    await expect(both).resolves.toEqual([{ ok: "me" }, { ok: "feeds" }]);
    expect(urls(calls).filter((u) => u === "/api/v1/auth/refresh")).toHaveLength(1);
  });

  it("재발급에 실패하면 SESSION_EXPIRED 오류를 던진다", async () => {
    stubFetch({
      "/api/v1/me": [() => json(401, {})],
      "/api/v1/auth/refresh": [() => json(401, { code: "REFRESH_TOKEN_REUSED" })],
    });

    const error = await http.get("/me").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(SESSION_EXPIRED);
  });

  it("재발급 뒤에도 401이면 다시 재발급하지 않는다", async () => {
    const calls = stubFetch({
      "/api/v1/me": [() => json(401, {}), () => json(401, {})],
      "/api/v1/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await expect(http.get("/me")).rejects.toMatchObject({ code: SESSION_EXPIRED });
    expect(urls(calls)).toEqual(["/api/v1/me", "/api/v1/auth/refresh", "/api/v1/me"]);
  });

  it("인증 경로(/auth/*)의 401은 재발급 없이 서버 오류 코드를 그대로 돌려준다", async () => {
    const calls = stubFetch({
      "/api/v1/auth/signup": [() => json(401, { code: "SIGNUP_SESSION_EXPIRED", message: "가입 시간이 지났어요." })],
    });

    await expect(http.get("/auth/signup")).rejects.toMatchObject({ status: 401, code: "SIGNUP_SESSION_EXPIRED" });
    expect(urls(calls)).toEqual(["/api/v1/auth/signup"]);
  });

  it("재발급은 Web Locks 잠금 안에서 보낸다 (다른 탭과 겹치지 않게)", async () => {
    const held: string[] = [];
    vi.stubGlobal("navigator", {
      locks: {
        request: async (name: string, task: () => Promise<unknown>) => {
          held.push(name);
          return task();
        },
      },
    });
    const calls = stubFetch({
      "/api/v1/me": [() => json(401, {}), () => json(200, {})],
      "/api/v1/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await http.get("/me");

    expect(held).toEqual([REFRESH_LOCK]);
    expect(urls(calls)).toContain("/api/v1/auth/refresh");
  });
});

describe("http: CSRF 헤더", () => {
  it("쓰기 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내고, GET에는 보내지 않는다", async () => {
    stubCookies("pickong_theme=dark; XSRF-TOKEN=abc%3D123");
    const calls = stubFetch({
      "/api/v1/me": [() => json(200, {}), () => json(200, {})],
    });

    await http.get("/me");
    await http.patch("/me", { bio: "안녕" });

    expect(calls.map((c) => [c.method, c.csrf])).toEqual([
      ["GET", undefined],
      ["PATCH", "abc=123"],
    ]);
  });

  it("재발급 요청(POST)에도 CSRF 헤더를 붙인다", async () => {
    stubCookies("XSRF-TOKEN=t1");
    const calls = stubFetch({
      "/api/v1/me": [() => json(401, {}), () => json(200, {})],
      "/api/v1/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await http.get("/me");

    expect(calls.find((c) => c.url === "/api/v1/auth/refresh")?.csrf).toBe("t1");
  });

  it("403 INVALID_CSRF_TOKEN이면 쿠키를 다시 읽어 한 번만 재시도한다", async () => {
    const doc = stubCookies("");
    const calls = stubFetch({
      "/api/v1/me": [
        () => {
          // 서버가 403 응답에 새 XSRF-TOKEN 쿠키를 담아 준 상황
          doc.cookie = "XSRF-TOKEN=fresh";
          return json(403, { code: "INVALID_CSRF_TOKEN" });
        },
        () => json(200, { ok: true }),
      ],
    });

    await expect(http.patch("/me", { bio: "x" })).resolves.toEqual({ ok: true });
    expect(calls.map((c) => c.csrf)).toEqual([undefined, "fresh"]);
  });

  it("재시도에서도 403이면 더 보내지 않고 오류를 돌려준다", async () => {
    stubCookies("XSRF-TOKEN=old");
    const calls = stubFetch({
      "/api/v1/me": [() => json(403, { code: "INVALID_CSRF_TOKEN" }), () => json(403, { code: "INVALID_CSRF_TOKEN", message: "새로고침" })],
    });

    await expect(http.patch("/me", {})).rejects.toMatchObject({ status: 403, code: "INVALID_CSRF_TOKEN" });
    expect(calls).toHaveLength(2);
  });

  it("CSRF가 아닌 403(권한 없음)은 재시도하지 않는다", async () => {
    stubCookies("XSRF-TOKEN=t");
    const calls = stubFetch({
      "/api/v1/me": [() => json(403, { code: "FORBIDDEN", message: "권한이 없어요." })],
    });

    await expect(http.delete("/me")).rejects.toMatchObject({ status: 403, code: "FORBIDDEN" });
    expect(calls).toHaveLength(1);
  });
});
