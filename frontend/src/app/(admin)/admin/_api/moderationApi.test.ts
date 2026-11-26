import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { moderationApi } from "./moderationApi";
import { invalidateModerationQueries } from "./cache";

afterEach(() => vi.unstubAllGlobals());
const sanction = { id: "9223372036854775806", type: "WARNING" as const, reason: "확인", startedAt: "2026-11-25T00:00:00Z", until: null };
const user = {
  id: "9223372036854775807", username: "user", nickname: "회원", profileImageUrl: null,
  isCreator: null, reportCount: 7, postCount: 9, status: "WITHDRAWN_PENDING", sanction, sanctions: [sanction],
};
const feed = {
  id: "d417931d-f2cc-40d0-9fd4-3d558d729a2b", type: "POST", title: "게시물", author: null,
  imageUrl: null, visibility: "PRIVATE", price: null, hidden: true, reportCount: 3, createdAt: "2026-11-25T00:00:00Z", link: null,
};
function mockResponse(status: number, body?: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("document", { cookie: "XSRF-TOKEN=csrf-value" });
  return fetchMock;
}

describe("확정된 관리자 API 계약", () => {
  it("검색·필터를 전달하며 큰 ID 문자열·확인 불가·탈퇴 상태를 보존한다", async () => {
    const fetchMock = mockResponse(200, [user]);
    expect(await moderationApi.users("검색", "REPORTED")).toEqual([user]);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/users?q=%EA%B2%80%EC%83%89&filter=REPORTED", expect.objectContaining({ method: "GET", credentials: "include" }));
  });

  it("등록은 AdminUser가 아닌 제재 결과를 반환하며 관리자 ID는 보내지 않는다", async () => {
    const fetchMock = mockResponse(201, sanction);
    expect(await moderationApi.sanction({ id: user.id, type: "WARNING", reason: "확인" })).toEqual(sanction);
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/admin/users/${user.id}/sanctions`, expect.objectContaining({
      method: "POST", body: JSON.stringify({ type: "WARNING", reason: "확인" }), headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-value" },
    }));
  });

  it("특정 제재 해제는 본문 없이 DELETE하며 204를 JSON으로 읽지 않는다", async () => {
    const fetchMock = mockResponse(204);
    await expect(moderationApi.revoke({ userId: user.id, sanctionId: sanction.id })).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/admin/users/${user.id}/sanctions/${sanction.id}`, expect.objectContaining({ method: "DELETE", body: undefined }));
  });

  it("선택 해제 사유는 합의된 DELETE 본문으로 보낸다", async () => {
    const fetchMock = mockResponse(204);
    await moderationApi.revoke({ userId: user.id, sanctionId: sanction.id, reason: "오인 제재" });
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ reason: "오인 제재" }));
  });

  it("게시물 목록은 feeds 경로를 사용하고 null 링크·이미지·작성자를 보존한다", async () => {
    const fetchMock = mockResponse(200, [feed]);
    expect(await moderationApi.feeds("")).toEqual([feed]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/feeds");
  });

  it("숨김 사유와 UUID를 PATCH에 전달하고 visibility를 보내지 않는다", async () => {
    const fetchMock = mockResponse(200, feed);
    expect(await moderationApi.setHidden({ id: feed.id, hidden: true, reason: "확인" })).toEqual(feed);
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/admin/feeds/${feed.id}`, expect.objectContaining({ method: "PATCH", body: JSON.stringify({ hidden: true, reason: "확인" }) }));
  });

  it("복구 사유 생략 시 hidden만 전송한다", async () => {
    const fetchMock = mockResponse(200, { ...feed, hidden: false });
    await moderationApi.setHidden({ id: feed.id, hidden: false });
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ hidden: false }));
  });

  it.each([401, 403, 404, 409, 500])("%s 오류를 성공으로 바꾸지 않는다", async (status) => {
    const fetchMock = mockResponse(status, { status, code: "SERVER_CODE", message: "서버 사유" });
    await expect(moderationApi.users("", "ALL")).rejects.toMatchObject({ status, code: "SERVER_CODE", message: "서버 사유" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("관리자 처리 후 캐시 갱신", () => {
  it("검색별 사용자·신고·콘텐츠와 회원용 게시물·댓글 캐시를 모두 무효화한다", async () => {
    const client = new QueryClient();
    const affected = [["admin", "users", "검색", "SANCTIONED"], ["admin", "reports", "PENDING"], ["admin", "contents", "POST", ""], ["posts", "detail", feed.id], ["posts", "comments", "old-long-id"]];
    for (const key of [...affected, ["places"]]) client.setQueryData(key, []);
    await invalidateModerationQueries(client);
    for (const key of affected) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    expect(client.getQueryState(["places"])?.isInvalidated).toBe(false);
    client.clear();
  });
});
