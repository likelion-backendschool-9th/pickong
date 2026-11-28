import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/errors";
import { ToastProvider } from "@/shared/ui/Toast";
import type { AdminPlaceDetail } from "../_lib/models";
import { AdminPlaceDetailScreen } from "./AdminPlaceDetailScreen";

const state = vi.hoisted(() => ({
  search: "",
  place: undefined as AdminPlaceDetail | undefined,
  pending: false,
  error: null as unknown,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(state.search),
}));

vi.mock("../_api/queries", () => ({
  adminPlaceKeys: { list: () => ["admin", "places", "list"], detail: (id: string) => ["admin", "places", "detail", id] },
  useAdminPlace: () => ({
    data: state.place,
    isPending: state.pending,
    isError: state.error != null,
    error: state.error,
    refetch: vi.fn(),
  }),
  useUpdateAdminPlace: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteAdminPlace: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

function detail(patch: Partial<AdminPlaceDetail> & Pick<AdminPlaceDetail, "id" | "sourceType">): AdminPlaceDetail {
  return {
    name: "찜콩 카페",
    address: "서울 성동구 성수동",
    category: "CAFE",
    imageUrl: "/images/photos/cafe.jpg",
    visibility: "PUBLIC",
    location: { lat: 37.5445, lng: 127.056 },
    businessHours: [{ dayOfWeek: 1, openTime: 540, closeTime: 1320, closed: false }],
    hoursExceptions: [{ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "임시 휴무" }],
    ...patch,
  };
}

function render(placeId = "a") {
  const client = new QueryClient();
  return renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(ToastProvider, null, createElement(AdminPlaceDetailScreen, { placeId }))));
}

describe("관리자 장소 상세 화면", () => {
  it("시스템 장소의 영업시간과 수정 삭제를 보여 준다", () => {
    state.search = "";
    state.pending = false;
    state.error = null;
    state.place = detail({ id: "a", sourceType: "SYSTEM" });
    const html = render();
    expect(html).toContain("찜콩 카페");
    expect(html).toContain("09:00–22:00");
    expect(html).toContain("미등록");
    expect(html).toContain("2026-12-25");
    expect(html).toContain("임시 휴무");
    expect(html).toContain("수정");
    expect(html).toContain("삭제");
    expect(html).toContain("관리 가능한 시스템 장소예요.");
  });

  it("다른 출처는 조회만 할 수 있고 수정 주소로 들어와도 폼을 열지 않는다", () => {
    state.search = "mode=edit";
    state.place = detail({ id: "f", name: "승인된 제보 장소", sourceType: "CUSTOM", imageUrl: null });
    const html = render("f");
    expect(html).toContain("시스템 제공 장소가 아니므로 조회만 할 수 있어요.");
    expect(html).not.toContain("저장");
    expect(html).not.toContain(">수정<");
    expect(html).not.toContain(">삭제<");
  });

  it("없는 장소와 주소가 없는 장소를 구분한다", () => {
    state.search = "";
    state.place = undefined;
    state.error = new ApiError(404, "PLACE_NOT_FOUND", "내부 메시지");
    const missing = render("missing");
    expect(missing).toContain("장소를 찾을 수 없어요.");
    expect(missing).not.toContain("내부 메시지");
    state.error = null;
    state.place = detail({ id: "e", sourceType: "SYSTEM", address: null, imageUrl: null, businessHours: [], hoursExceptions: [] });
    expect(render("e")).toContain("주소 미등록");
  });

  it("수정 모드에는 기존 이름이 들어 있고 변경 전에는 저장할 수 없다", () => {
    state.search = "mode=edit";
    state.error = null;
    state.place = detail({ id: "a", sourceType: "SYSTEM" });
    const html = render();
    expect(html).toContain('value="찜콩 카페"');
    expect(html).toContain("disabled=\"\"");
    expect(html).not.toContain("시스템 제공 장소로 등록돼요.");
  });
});
