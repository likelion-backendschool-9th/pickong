import { describe, expect, it } from "vitest";
import { ApiError, SESSION_EXPIRED, TERMS_RECONSENT_REQUIRED } from "@/shared/api/errors";
import { isFatalProbeError } from "./connection";

describe("isFatalProbeError", () => {
  it("재발급 실패(401) · 약관 재동의(403)면 다시 연결하지 않는다", () => {
    expect(isFatalProbeError(new ApiError(401, SESSION_EXPIRED, "로그인"))).toBe(true);
    expect(isFatalProbeError(new ApiError(403, TERMS_RECONSENT_REQUIRED, "재동의"))).toBe(true);
  });

  it("네트워크 오류 · 서버 오류면 계속 다시 연결한다", () => {
    expect(isFatalProbeError(new TypeError("Failed to fetch"))).toBe(false);
    expect(isFatalProbeError(new ApiError(500, "INTERNAL_SERVER_ERROR", "오류"))).toBe(false);
  });
});
