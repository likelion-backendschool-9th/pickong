import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, SESSION_EXPIRED } from "./errors";
import { http } from "./http";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/** 경로별 응답을 차례로 돌려주는 가짜 fetch */
function stubFetch(responses: Record<string, (() => Response | Promise<Response>)[]>) {
  const calls: string[] = [];
  const fetchMock = vi.fn(async (url: string) => {
    calls.push(url);
    const queue = responses[url];
    const next = queue?.shift();
    if (!next) throw new Error(`예상하지 못한 요청: ${url}`);
    return next();
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

describe("http: Access Token 재발급", () => {
  beforeEach(() => vi.unstubAllGlobals());
  afterEach(() => vi.unstubAllGlobals());

  it("401을 받으면 한 번 재발급하고 같은 요청을 다시 보낸다", async () => {
    const calls = stubFetch({
      "/api/me": [() => json(401, { code: "UNAUTHORIZED" }), () => json(200, { username: "seoyeon" })],
      "/api/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await expect(http.get("/me")).resolves.toEqual({ username: "seoyeon" });
    expect(calls).toEqual(["/api/me", "/api/auth/refresh", "/api/me"]);
  });

  it("동시에 401을 받은 요청들은 재발급 요청 하나를 함께 기다린다", async () => {
    let finishRefresh!: () => void;
    const refreshDone = new Promise<Response>((resolve) => {
      finishRefresh = () => resolve(new Response(null, { status: 204 }));
    });
    const calls = stubFetch({
      "/api/me": [() => json(401, {}), () => json(200, { ok: "me" })],
      "/api/feeds": [() => json(401, {}), () => json(200, { ok: "feeds" })],
      "/api/auth/refresh": [() => refreshDone],
    });

    const both = Promise.all([http.get("/me"), http.get("/feeds")]);
    await vi.waitFor(() => expect(calls.filter((c) => c === "/api/auth/refresh")).toHaveLength(1));
    finishRefresh();

    await expect(both).resolves.toEqual([{ ok: "me" }, { ok: "feeds" }]);
    expect(calls.filter((c) => c === "/api/auth/refresh")).toHaveLength(1);
  });

  it("재발급에 실패하면 SESSION_EXPIRED 오류를 던진다", async () => {
    stubFetch({
      "/api/me": [() => json(401, {})],
      "/api/auth/refresh": [() => json(401, { code: "REFRESH_TOKEN_REUSED" })],
    });

    const error = await http.get("/me").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(SESSION_EXPIRED);
  });

  it("재발급 뒤에도 401이면 다시 재발급하지 않는다", async () => {
    const calls = stubFetch({
      "/api/me": [() => json(401, {}), () => json(401, {})],
      "/api/auth/refresh": [() => new Response(null, { status: 204 })],
    });

    await expect(http.get("/me")).rejects.toMatchObject({ code: SESSION_EXPIRED });
    expect(calls).toEqual(["/api/me", "/api/auth/refresh", "/api/me"]);
  });

  it("인증 경로(/auth/*)의 401은 재발급 없이 서버 오류 코드를 그대로 돌려준다", async () => {
    const calls = stubFetch({
      "/api/auth/signup": [() => json(401, { code: "SIGNUP_SESSION_EXPIRED", message: "가입 시간이 지났어요." })],
    });

    await expect(http.get("/auth/signup")).rejects.toMatchObject({ status: 401, code: "SIGNUP_SESSION_EXPIRED" });
    expect(calls).toEqual(["/api/auth/signup"]);
  });
});
