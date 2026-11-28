import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/shared/ui/Toast";
import { AdminPlaceCreateScreen } from "./AdminPlaceCreateScreen";

const mutateAsync = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
}));

vi.mock("../_api/queries", () => ({
  useCreateAdminPlace: () => ({ mutateAsync }),
}));

describe("관리자 장소 등록 화면", () => {
  it("빈 폼으로 시작하고 지도 중심을 장소 좌표로 확정하지 않는다", () => {
    const html = renderToStaticMarkup(createElement(ToastProvider, null, createElement(AdminPlaceCreateScreen)));
    expect(html).toContain("장소 추가");
    expect(html).toContain("시스템 제공 장소로 등록돼요.");
    expect(html).toContain("카테고리를 선택해 주세요");
    expect(html).toContain("아직 위치를 지정하지 않았어요.");
    expect(html).toContain("지도를 불러오는 중…");
    expect(html).toContain("이 위치로 지정");
    expect(html).toContain("입력한 좌표 사용");
    expect(html).toContain("미등록");
    expect(html).toContain("예외 영업시간");
    expect(html).toContain("JPEG, PNG, WEBP");
    expect(html).toContain("등록");
    expect(html).toContain("취소");
    expect(html).not.toContain("공개 범위");
    expect(html).not.toContain("sourceType");
    expect(mutateAsync).not.toHaveBeenCalled();
  });
});
