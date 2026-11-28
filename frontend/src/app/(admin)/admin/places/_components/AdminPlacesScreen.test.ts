import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/errors";
import { ToastProvider } from "@/shared/ui/Toast";
import type { AdminPlaceSummary } from "../_lib/models";
import { AdminPlacesScreen, leaveAdminPlaces } from "./AdminPlacesScreen";

const state = vi.hoisted(() => ({
  search: "",
  places: [] as AdminPlaceSummary[] | undefined,
  pending: false,
  error: null as unknown,
  refetch: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(state.search),
}));

vi.mock("../_api/queries", () => ({
  adminPlaceKeys: { list: () => ["admin", "places", "list"], detail: (id: string) => ["admin", "places", "detail", id] },
  useAdminPlaces: () => ({
    data: state.places,
    isPending: state.pending,
    isError: state.error != null,
    error: state.error,
    refetch: state.refetch,
  }),
  useDeleteAdminPlace: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

function place(patch: Partial<AdminPlaceSummary> & Pick<AdminPlaceSummary, "id" | "name" | "sourceType">): AdminPlaceSummary {
  return { address: "서울", category: "CAFE", imageUrl: null, ...patch };
}

const places = [
  place({ id: "a", name: "찜콩 카페", address: "서울 성동구 성수동", category: "CAFE", sourceType: "SYSTEM", imageUrl: "/images/photos/cafe.jpg" }),
  place({ id: "e", name: "주소 없는 장소", address: null, category: "ETC", sourceType: "SYSTEM", imageUrl: "" }),
  place({ id: "f", name: "승인된 제보 장소", address: "서울 마포구", category: "PARK", sourceType: "CUSTOM" }),
  place({ id: "g", name: "추출된 장소", address: "서울 용산구", category: "EXHIBITION", sourceType: "AI_EXTRACTED" }),
  place({ id: "j", name: "출처를 알 수 없는 장소", address: "서울 중구", category: "PHOTO_SPOT", sourceType: "KAKAO" }),
];

function render() {
  const client = new QueryClient();
  return renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(ToastProvider, null, createElement(AdminPlacesScreen))));
}

beforeEach(() => {
  state.search = "";
  state.places = places;
  state.pending = false;
  state.error = null;
  state.refetch.mockReset();
});

describe("관리자 장소 목록 화면", () => {
  it("조회한 장소를 출처와 관리 상태와 함께 보여 주고 상세와 수정으로 이동한다", () => {
    const html = render();
    expect(html).toContain("찜콩 카페");
    expect(html).toContain("서울 성동구 성수동");
    expect(html).toContain("주소 미등록");
    expect(html).toContain("카페");
    expect(html).toContain("시스템");
    expect(html).toContain("사용자 등록");
    expect(html).toContain("AI 추출");
    expect(html).toContain("기타 출처");
    expect(html).toContain("KAKAO");
    expect(html).toContain("관리 가능");
    expect(html).toContain("읽기 전용");
    expect(html).toContain("전체 5곳");
    expect(html).toContain('src="/images/photos/cafe.jpg"');
    expect(html).toContain("사진 없음");
    expect(html).toContain('href="/admin/places/new"');
    expect(html).toContain('href="/admin/places/a"');
    expect(html).toContain('href="/admin/places/a?mode=edit"');
    expect(html).toContain("장소 추가");
    expect(html).toContain("삭제");
  });

  it("읽기 전용 장소에는 수정과 삭제 버튼을 두지 않는다", () => {
    state.places = [places[4]];
    const html = render();
    expect(html).toContain("상세");
    expect(html).not.toContain("수정");
    expect(html).not.toContain("삭제");
    expect(html).toContain("읽기 전용");
  });

  it("URL에 저장된 검색 조건을 목록에 적용한다", () => {
    state.search = "q=성수&category=CAFE&source=SYSTEM";
    const html = render();
    expect(html).toContain('value="성수"');
    expect(html).toContain("찜콩 카페");
    expect(html).not.toContain("승인된 제보 장소");
    expect(html).toContain("검색 결과 1곳");
    expect(html).toContain("list=");
    expect(html).toContain("초기화");
  });

  it("잘못된 검색 조건은 전체 목록으로 보고 결과가 없으면 안내한다", () => {
    state.search = "category=FOOD&source=KAKAO";
    expect(render()).toContain("전체 5곳");
    state.search = "q=없는장소";
    const html = render();
    expect(html).toContain("검색 조건에 맞는 장소가 없어요.");
    expect(html).toContain("검색 조건 초기화");
  });

  it("로딩, 빈 목록, 오류를 서로 구분한다", () => {
    state.pending = true;
    state.places = undefined;
    expect(render()).not.toContain("등록된 장소가 없어요.");
    state.pending = false;
    state.places = [];
    expect(render()).toContain("등록된 장소가 없어요.");
    state.places = undefined;
    state.error = new ApiError(500, "INTERNAL", "stack trace secret");
    const html = render();
    expect(html).toContain("장소 목록을 불러오지 못했어요.");
    expect(html).toContain("다시 시도");
    expect(html).not.toContain("stack trace secret");
    state.places = places;
    const stale = render();
    expect(stale).toContain("찜콩 카페");
    expect(stale).toContain("최신 목록을 불러오지 못했어요.");
    expect(stale).not.toContain("stack trace secret");
  });

  it("목록의 뒤로가기는 방문 기록 없이 관리자 메인으로 간다", () => {
    const push = vi.fn();
    const back = vi.fn();
    leaveAdminPlaces(push);
    expect(push).toHaveBeenCalledWith("/admin");
    expect(back).not.toHaveBeenCalled();
  });
});
