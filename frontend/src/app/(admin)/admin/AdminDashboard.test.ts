import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { AdminDashboard } from "./AdminDashboard";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}));

vi.mock("./_api/adminApi", () => ({
  useAdminSummary: () => ({ data: undefined }),
}));

vi.mock("@/entities/place-submission", () => ({
  usePlaceSubmissions: () => ({ data: [] }),
}));

vi.mock("@/shared/auth/authApi", () => ({
  authApi: { logout: vi.fn() },
}));

describe("관리자 대시보드 메뉴", () => {
  it("지도 관리를 제보 관리 옆에 두고 기존 메뉴 경로는 유지한다", () => {
    const client = new QueryClient();
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(AdminDashboard)));
    expect(html).toContain("지도 관리");
    expect(html).toContain("공용 장소 조회 및 관리");
    expect(html).toContain('href="/admin/places"');
    expect(html.indexOf("제보 관리")).toBeLessThan(html.indexOf("지도 관리"));
    expect(html).toContain('href="/admin/reports"');
    expect(html).toContain('href="/admin/users"');
    expect(html).toContain('href="/admin/contents"');
    expect(html).toContain('href="/admin/place-submissions"');
    expect(html).toContain('href="/admin/creators"');
    expect(html).toContain('href="/admin/refunds"');
    expect(html).toContain('href="/admin/settlements"');
    expect(html).not.toContain('href="/admin/places/new"');
  });
});
