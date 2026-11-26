import { createElement, type ComponentType } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, type MutationOptions } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useLiftSanction, useResolveReport, useSanctionUser, useSetContentHidden } from "./adminApi";

const captured = vi.hoisted(() => ({ client: null as QueryClient | null, options: null as MutationOptions<unknown, Error, unknown> | null }));

// React의 훅 경계만 캡처합니다. mutationFn·콜백·HTTP 래퍼·MutationCache는 실제 구현을 사용합니다.
vi.mock("@tanstack/react-query", async (load) => {
  const original = await load<typeof import("@tanstack/react-query")>();
  return {
    ...original,
    useQueryClient: () => captured.client,
    useMutation: (options: MutationOptions<unknown, Error, unknown>) => { captured.options = options; return {}; },
  };
});

function Sanction() { useSanctionUser(); return null; }
function Revoke() { useLiftSanction(); return null; }
function Hide() { useSetContentHidden(); return null; }
function Resolve() { useResolveReport(); return null; }

const sanction = { id: "71", type: "WARNING", reason: "확인", startedAt: "2026-11-25T00:00:00Z", until: null };
const feed = { id: "d417931d-f2cc-40d0-9fd4-3d558d729a2b", type: "POST", title: "게시물", author: null, imageUrl: null,
  link: null, visibility: "PRIVATE", price: null, hidden: true, reportCount: 2, createdAt: "2026-11-25T00:00:00Z" };
const cases: [string, ComponentType, unknown, number, unknown][] = [
  ["제재 등록", Sanction, { id: "1", type: "WARNING", reason: "확인" }, 201, sanction],
  ["개별 해제", Revoke, { userId: "1", sanctionId: "71" }, 204, undefined],
  ["게시물 숨김", Hide, { id: feed.id, hidden: true, reason: "확인" }, 200, feed],
  ["신고 처리", Resolve, { id: "91", action: "HIDE_CONTENT", memo: "확인" }, 200, { id: "91", status: "RESOLVED" }],
];
const affected = [["admin", "users", "", "ALL"], ["admin", "reports", "PENDING"], ["admin", "contents", "POST", ""], ["posts", "detail", feed.id], ["posts", "comments", "legacy-id"]];

beforeEach(() => {
  captured.client = new QueryClient({ defaultOptions: { mutations: { retry: false, gcTime: 0 } } });
  captured.options = null;
  for (const key of [...affected, ["places"]]) captured.client.setQueryData(key, ["기존 값"]);
  vi.stubGlobal("document", { cookie: "XSRF-TOKEN=valid-csrf" });
});
afterEach(() => { captured.client?.clear(); vi.unstubAllGlobals(); });

function mutation(component: ComponentType) {
  renderToStaticMarkup(createElement(component));
  if (!captured.options || !captured.client) throw new Error("관리자 mutation이 등록되지 않았습니다.");
  return captured.client.getMutationCache().build(captured.client, captured.options);
}
function expectCacheState(invalidated: boolean) {
  for (const key of affected) {
    expect(captured.client?.getQueryState(key)?.isInvalidated).toBe(invalidated);
    expect(captured.client?.getQueryData(key)).toEqual(["기존 값"]);
  }
  expect(captured.client?.getQueryState(["places"])?.isInvalidated).toBe(false);
}

describe.each(cases)("%s의 실제 mutation 수명주기", (_name, component, input, status, result) => {
  it("서버 성공 뒤에만 관리자·게시물·댓글 캐시를 갱신한다", async () => {
    let complete!: (value: Response) => void;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>((resolve) => { complete = resolve; })));
    const operation = mutation(component);
    const running = operation.execute(input);
    await vi.waitFor(() => expect(complete).toBeTypeOf("function"));
    expectCacheState(false);
    complete(new Response(result === undefined ? null : JSON.stringify(result), { status }));
    await running;
    expect(operation.state.status).toBe("success");
    expectCacheState(true);
  });

  it.each([404, 409])("상태 충돌 %s는 실패로 유지하면서 오래된 캐시를 갱신한다", async (status) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ status, code: "STATE_CHANGED", message: "이미 변경됨" }), { status })));
    const operation = mutation(component);
    await expect(operation.execute(input)).rejects.toMatchObject({ status, code: "STATE_CHANGED" });
    expect(operation.state.status).toBe("error");
    expectCacheState(true);
  });

  it("권한 거절은 재발급·업무 재시도·성공 캐시 변경 없이 표시한다", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 403, code: "FORBIDDEN", message: "권한 없음" }), { status: 403 }));
    vi.stubGlobal("fetch", fetchMock);
    const operation = mutation(component);
    await expect(operation.execute(input)).rejects.toMatchObject({ status: 403, code: "FORBIDDEN" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(operation.state.status).toBe("error");
    expectCacheState(false);
  });
});
