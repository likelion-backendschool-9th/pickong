import { createElement, type ComponentType } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryObserver, type MutationOptions } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminPlaceCreateInput } from "../_lib/models";
import { adminPlaceApi } from "./adminPlaceApi";
import { adminPlaceKeys, useCreateAdminPlace, useDeleteAdminPlace, useUpdateAdminPlace } from "./queries";

const captured = vi.hoisted(() => ({ client: null as QueryClient | null, options: null as MutationOptions<unknown, Error, unknown> | null }));

vi.mock("@tanstack/react-query", async (load) => {
  const original = await load<typeof import("@tanstack/react-query")>();
  return {
    ...original,
    useQueryClient: () => captured.client,
    useMutation: (options: MutationOptions<unknown, Error, unknown>) => {
      captured.options = options;
      return {};
    },
  };
});

const id = "3f1c0c2e-6b4a-4d2e-9c1a-8b7e6d5c4b3a";
const detail = {
  id, name: "성심당", address: "대전", category: "CAFE", sourceType: "SYSTEM", visibility: "PUBLIC",
  lat: 36.3, lng: 127.4, imageUrl: null, businessHours: [], hoursExceptions: [],
};
const createInput: AdminPlaceCreateInput = {
  name: "성심당", address: "대전", category: "CAFE", location: { lat: 36.3, lng: 127.4 }, businessHours: [], hoursExceptions: [],
};

function Create() { useCreateAdminPlace(); return null; }
function Update() { useUpdateAdminPlace(); return null; }
function Remove() { useDeleteAdminPlace(); return null; }

const cases: [string, ComponentType, unknown, number, unknown][] = [
  ["등록", Create, createInput, 201, detail],
  ["수정", Update, { id, businessHours: [] }, 200, detail],
  ["삭제", Remove, id, 204, undefined],
];

beforeEach(() => {
  captured.client = new QueryClient({ defaultOptions: { mutations: { retry: false, gcTime: 0 } } });
  captured.options = null;
  captured.client.setQueryData(adminPlaceKeys.list(), ["목록"]);
  captured.client.setQueryData(adminPlaceKeys.detail(id), { id, name: "이전" });
  captured.client.setQueryData(["admin", "reports", "PENDING"], ["신고"]);
  captured.client.setQueryData(["places"], ["회원 장소"]);
  vi.stubGlobal("document", { cookie: "XSRF-TOKEN=valid-csrf" });
});
afterEach(() => { captured.client?.clear(); vi.unstubAllGlobals(); });

function mutation(component: ComponentType) {
  renderToStaticMarkup(createElement(component));
  if (!captured.options || !captured.client) throw new Error("관리자 장소 mutation이 등록되지 않았습니다.");
  return captured.client.getMutationCache().build(captured.client, captured.options);
}

describe.each(cases)("%s", (name, component, input, status, result) => {
  it("성공 후에만 장소 목록을 갱신하고 다른 캐시는 그대로 둡니다", async () => {
    let complete!: (value: Response) => void;
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>((resolve) => { complete = resolve; })));
    const operation = mutation(component);
    const running = operation.execute(input);
    await vi.waitFor(() => expect(complete).toBeTypeOf("function"));
    expect(captured.client?.getQueryState(adminPlaceKeys.list())?.isInvalidated).toBe(false);
    complete(new Response(result === undefined ? null : JSON.stringify(result), { status }));
    await running;
    expect(operation.state.status).toBe("success");
    expect(captured.client?.getQueryState(adminPlaceKeys.list())?.isInvalidated).toBe(true);
    expect(captured.client?.getQueryState(["admin", "reports", "PENDING"])?.isInvalidated).toBe(false);
    expect(captured.client?.getQueryState(["places"])?.isInvalidated).toBe(false);
    if (name === "수정") expect(captured.client?.getQueryData(adminPlaceKeys.detail(id))).toMatchObject({ name: "성심당" });
    if (name === "삭제") expect(captured.client?.getQueryData(adminPlaceKeys.detail(id))).toBeUndefined();
    if (name === "등록") expect(captured.client?.getQueryData(adminPlaceKeys.detail(id))).toEqual({ id, name: "이전" });
  });

  it("실패하면 성공으로 보이지 않고 캐시를 지우지 않습니다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "PLACE_NOT_FOUND", message: "장소를 찾을 수 없어요." }), { status: 404 })));
    const operation = mutation(component);
    await expect(operation.execute(input)).rejects.toMatchObject({ status: 404, code: "PLACE_NOT_FOUND" });
    expect(operation.state.status).toBe("error");
    expect(captured.client?.getQueryState(adminPlaceKeys.list())?.isInvalidated).toBe(false);
    expect(captured.client?.getQueryData(adminPlaceKeys.detail(id))).toEqual({ id, name: "이전" });
  });
});

it("삭제 성공 뒤에 도착한 상세 응답은 캐시를 되돌리지 않습니다", async () => {
  let resolveDetail!: (value: Response) => void;
  vi.stubGlobal(
    "fetch",
    vi.fn((_url: RequestInfo, init?: RequestInit) => {
      if (init?.method === "DELETE") return Promise.resolve(new Response(null, { status: 204 }));
      return new Promise<Response>((resolve) => {
        resolveDetail = resolve;
      });
    }),
  );
  const client = captured.client;
  if (!client) throw new Error("query client");
  const running = client.fetchQuery({
    queryKey: adminPlaceKeys.detail(id),
    queryFn: ({ signal }) => adminPlaceApi.detail(id, signal),
  });
  const operation = mutation(Remove);
  await operation.execute(id);
  resolveDetail(new Response(JSON.stringify(detail), { status: 200 }));
  await running.catch(() => undefined);
  expect(client.getQueryData(adminPlaceKeys.detail(id))).toBeUndefined();
});

it("목록 재조회가 실패해도 성공한 변경을 다시 보내지 않습니다", async () => {
  let deletes = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn((_url: RequestInfo, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        deletes += 1;
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(new Response(JSON.stringify({ code: "INTERNAL", message: "목록을 불러오지 못했어요." }), { status: 500 }));
    }),
  );
  const client = captured.client;
  if (!client) throw new Error("query client");
  const observer = new QueryObserver(client, { queryKey: adminPlaceKeys.list(), queryFn: () => adminPlaceApi.list() });
  const unsubscribe = observer.subscribe(() => undefined);
  await observer.refetch();
  const operation = mutation(Remove);
  await operation.execute(id);
  await vi.waitFor(() => expect(observer.getCurrentResult().isError).toBe(true));
  expect(deletes).toBe(1);
  expect(operation.state.status).toBe("success");
  unsubscribe();
});
