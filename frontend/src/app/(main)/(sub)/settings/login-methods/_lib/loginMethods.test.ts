import { describe, expect, it } from "vitest";
import { isSameOriginPath, linkErrorMessage, loginMethodRows } from "./loginMethods";

const kakao = { provider: "KAKAO" as const, email: "a@kakao.com", connectedAt: "2026-11-01T00:00:00+09:00" };
const google = { provider: "GOOGLE" as const, email: "a@gmail.com", connectedAt: "2026-11-02T00:00:00+09:00" };

describe("loginMethodRows", () => {
  it("카카오 · Google 순서로 연결 여부를 보여 준다", () => {
    const rows = loginMethodRows([google]);
    expect(rows.map((r) => r.provider)).toEqual(["KAKAO", "GOOGLE"]);
    expect(rows[0].account).toBeNull();
    expect(rows[1].account).toBe(google);
  });

  it("마지막 남은 로그인 수단은 해제할 수 없다", () => {
    const rows = loginMethodRows([kakao]);
    expect(rows[0].canUnlink).toBe(false);
    expect(rows[1].canUnlink).toBe(false);
  });

  it("둘 다 연결돼 있으면 어느 쪽이든 해제할 수 있다", () => {
    expect(loginMethodRows([kakao, google]).every((r) => r.canUnlink)).toBe(true);
  });
});

describe("linkErrorMessage", () => {
  it("다른 찜콩 계정에 연결된 소셜 계정은 명확히 안내한다", () => {
    expect(linkErrorMessage("SOCIAL_ACCOUNT_ALREADY_LINKED")).toContain("이미 다른 찜콩 계정");
  });

  it("사유가 없으면 null, 모르는 사유는 일반 실패 문구", () => {
    expect(linkErrorMessage(null)).toBeNull();
    expect(linkErrorMessage("UNKNOWN")).toBe(linkErrorMessage("OAUTH_FAILED"));
  });
});

describe("isSameOriginPath", () => {
  it("같은 출처의 경로만 허용한다", () => {
    expect(isSameOriginPath("/api/v1/auth/oauth2/authorization/google?link=abc")).toBe(true);
    expect(isSameOriginPath("//evil.example/x")).toBe(false);
    expect(isSameOriginPath("https://evil.example/x")).toBe(false);
  });
});
