import { describe, expect, it } from "vitest";
import { isSameOriginPath, linkErrorMessage, loginMethodRows } from "./loginMethods";

const kakao = { provider: "KAKAO" as const, email: "a@kakao.com", connectedAt: "2026-11-01T00:00:00+09:00", canUnlink: false };
const google = { provider: "GOOGLE" as const, email: "a@gmail.com", connectedAt: "2026-11-02T00:00:00+09:00", canUnlink: true };

describe("loginMethodRows", () => {
  it("카카오 · Google 순서로 연결 여부를 보여 준다", () => {
    const rows = loginMethodRows([{ ...google, canUnlink: false }]);
    expect(rows.map((r) => r.provider)).toEqual(["KAKAO", "GOOGLE"]);
    expect(rows[0].account).toBeNull();
    expect(rows[1].account?.provider).toBe("GOOGLE");
  });

  it("해제 가능 여부는 서버의 canUnlink를 그대로 쓰고, 연결됐지만 해제할 수 없으면 가입한 수단이다", () => {
    const [k, g] = loginMethodRows([kakao, google]);
    expect([k.canUnlink, k.isSignupMethod]).toEqual([false, true]);
    expect([g.canUnlink, g.isSignupMethod]).toEqual([true, false]);
  });

  it("연결되지 않은 줄은 해제할 수 없고 가입한 수단도 아니다", () => {
    const [, g] = loginMethodRows([kakao]);
    expect([g.canUnlink, g.isSignupMethod]).toEqual([false, false]);
  });
});

describe("linkErrorMessage", () => {
  it("다른 찜콩 계정에 연결된 소셜 계정은 명확히 안내한다", () => {
    expect(linkErrorMessage("SOCIAL_ACCOUNT_ALREADY_LINKED")).toContain("이미 다른 찜콩 계정");
  });

  it("다른 회원의 이메일 · 이용할 수 없는 계정은 일반 실패가 아닌 전용 문구로 안내한다", () => {
    expect(linkErrorMessage("EMAIL_ALREADY_REGISTERED")).toContain("이메일");
    expect(linkErrorMessage("ACCOUNT_UNAVAILABLE")).not.toBe(linkErrorMessage("OAUTH_FAILED"));
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
