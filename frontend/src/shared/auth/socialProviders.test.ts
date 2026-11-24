import { describe, expect, it } from "vitest";
import { oauthAuthorizeUrl, parseSocialProvider, providerSlug } from "./socialProviders";

describe("socialProviders", () => {
  it("로그인 시작 주소는 /api/v1 아래 Spring Security 인가 경로를 쓴다", () => {
    expect(oauthAuthorizeUrl("KAKAO")).toBe("/api/v1/auth/oauth2/authorization/kakao");
    expect(oauthAuthorizeUrl("GOOGLE")).toBe("/api/v1/auth/oauth2/authorization/google");
  });

  it("주소에는 소문자 이름을 쓴다", () => {
    expect(providerSlug("KAKAO")).toBe("kakao");
  });

  it("대소문자와 관계없이 제공자를 읽고, 모르는 값은 null", () => {
    expect(parseSocialProvider("kakao")).toBe("KAKAO");
    expect(parseSocialProvider("GOOGLE")).toBe("GOOGLE");
    expect(parseSocialProvider("naver")).toBeNull();
    expect(parseSocialProvider(null)).toBeNull();
  });
});
