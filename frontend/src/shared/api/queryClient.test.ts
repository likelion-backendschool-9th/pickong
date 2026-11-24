import { describe, expect, it } from "vitest";
import { ApiError, SESSION_EXPIRED, TERMS_RECONSENT_REQUIRED } from "./errors";
import { redirectTargetFor } from "./queryClient";

const at = (pathname: string, search = "") => ({ pathname, search });

describe("redirectTargetFor", () => {
  it("재발급까지 실패하면 로그인 화면으로 보낸다", () => {
    expect(redirectTargetFor(new ApiError(401, SESSION_EXPIRED, ""), at("/feed"))).toBe("/login?error=SESSION_EXPIRED");
  });

  it("재동의가 필요하면 지금 주소(검색어 포함)를 next로 넘겨 재동의 화면으로 보낸다", () => {
    expect(redirectTargetFor(new ApiError(403, TERMS_RECONSENT_REQUIRED, ""), at("/map", "?tab=saved"))).toBe(
      "/terms/consent?next=%2Fmap%3Ftab%3Dsaved",
    );
  });

  it("이미 재동의 화면이면 보내지 않는다", () => {
    expect(redirectTargetFor(new ApiError(403, TERMS_RECONSENT_REQUIRED, ""), at("/terms/consent", "?next=%2Ffeed"))).toBeNull();
  });

  it("그 밖의 오류는 보내지 않는다", () => {
    expect(redirectTargetFor(new ApiError(403, "FORBIDDEN", ""), at("/feed"))).toBeNull();
    expect(redirectTargetFor(new Error("network"), at("/feed"))).toBeNull();
  });
});
