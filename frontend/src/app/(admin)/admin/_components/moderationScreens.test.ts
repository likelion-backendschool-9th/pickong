import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/shared/ui/Toast";
import { ApiError } from "@/shared/api/errors";
import type { AdminContent, AdminUser } from "../_lib/models";
import { AdminUsersScreen } from "../users/AdminUsersScreen";
import { ContentsScreen } from "../contents/ContentsScreen";

const state = vi.hoisted(() => ({
  search: "", users: [] as AdminUser[], contents: [] as AdminContent[], pending: false, error: null as unknown,
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(state.search), useRouter: () => ({ back: vi.fn(), push: vi.fn() }),
}));
vi.mock("../_api/adminApi", () => {
  const query = (data: unknown) => ({ data, isPending: state.pending, isError: !!state.error, error: state.error, refetch: vi.fn(), isPlaceholderData: false });
  const mutation = () => ({ isPending: false, isError: false, mutate: vi.fn(), reset: vi.fn() });
  return {
    useAdminUsers: () => query(state.users), useAdminContents: () => query(state.contents),
    useSanctionUser: mutation, useLiftSanction: mutation, useSetContentHidden: mutation,
  };
});

function user(patch: Partial<AdminUser> = {}): AdminUser {
  return { id: "1", username: "member", nickname: "회원", profileImageUrl: null, isCreator: null,
    status: "ACTIVE", reportCount: 7, postCount: 9, sanction: null, sanctions: [], ...patch };
}
function render(component: typeof AdminUsersScreen | typeof ContentsScreen) {
  return renderToStaticMarkup(createElement(ToastProvider, null, createElement(component)));
}
beforeEach(() => { state.search = ""; state.users = []; state.contents = []; state.pending = false; state.error = null; });

describe("실제 응답을 표시하는 관리자 화면", () => {
  it("탈퇴 상태와 확인 불가를 표시하고 신규 제재를 막는다", () => {
    state.users = [user({ status: "WITHDRAWN_PENDING" }), user({ id: "2", status: "WITHDRAWN" })];
    const html = render(AdminUsersScreen);
    expect(html).toContain("탈퇴 진행 중");
    expect(html).toContain("탈퇴 완료");
    expect(html).toContain("크리에이터 여부 확인 불가");
    expect(html).toContain("게시물 9 · 신고 7");
    expect(html.match(/disabled=""/g)).toHaveLength(2);
  });

  it("대표 제재에 가리지 않고 모든 유효한 제재에 해제 버튼을 보여 준다", () => {
    const warning = { id: "3", type: "WARNING" as const, reason: "경고 사유", startedAt: "2026-11-25T00:00:00Z", until: null };
    const ban = { ...warning, id: "4", type: "BAN" as const, reason: "정지 사유" };
    state.users = [user({ status: "BANNED", sanction: ban, sanctions: [ban, warning] })];
    const html = render(AdminUsersScreen);
    expect(html).toContain("경고 사유");
    expect(html).toContain("정지 사유");
    expect(html).toContain('aria-label="회원 경고 해제"');
    expect(html).toContain('aria-label="회원 영구 정지 해제"');
  });

  it("nullable 콘텐츠 링크를 강제로 생성하지 않는다", () => {
    state.contents = [{ id: "uuid", type: "POST", title: "비공개 게시물", author: null, imageUrl: null, link: null,
      visibility: "PRIVATE", hidden: true, price: null, reportCount: 2, createdAt: "2026-11-25T00:00:00Z" }];
    const html = render(ContentsScreen);
    expect(html).toContain("비공개 게시물");
    expect(html).toContain("알 수 없음");
    expect(html).toContain('aria-checked="true"');
    expect(html).not.toContain("/posts/");
  });

  it("컬렉션 탭을 유지하고 기존 게시물 데이터 대신 준비 안내를 보여 준다", () => {
    state.search = "type=COLLECTION";
    const html = render(ContentsScreen);
    expect(html).toContain("컬렉션 관리 연동 준비 중");
    expect(html).toContain('role="tab" aria-selected="true"');
    expect(html).not.toContain('role="switch"');
  });

  it("URL의 사용자 검색·필터를 복원한다", () => {
    state.search = "q=%ED%9A%8C%EC%9B%90&filter=SANCTIONED";
    const html = render(AdminUsersScreen);
    expect(html).toContain('value="회원"');
    expect(html).toContain('aria-pressed="true"');
  });

  it("빈 데이터와 로딩을 구분한다", () => {
    expect(render(AdminUsersScreen)).toContain("해당하는 사용자가 없어요");
    state.pending = true;
    const html = render(AdminUsersScreen);
    expect(html).toContain("animate-pulse");
    expect(html).not.toContain("해당하는 사용자가 없어요");
  });

  it("관리자 권한 오류는 목록 대신 메시지로 표시한다", () => {
    state.error = new ApiError(403, "FORBIDDEN", "관리자 권한이 필요합니다");
    expect(render(ContentsScreen)).toContain("관리자 권한이 필요합니다");
    expect(render(AdminUsersScreen)).toContain("관리자 권한이 필요합니다");
  });
});
