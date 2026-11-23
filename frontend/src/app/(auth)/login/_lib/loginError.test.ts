import { describe, expect, it } from "vitest";
import { loginErrorMessage } from "./loginError";

describe("loginErrorMessage", () => {
  it("사유가 없으면 아무것도 보여 주지 않는다", () => {
    expect(loginErrorMessage(null)).toBeNull();
    expect(loginErrorMessage("")).toBeNull();
  });

  it("아는 사유는 그에 맞는 문구를 돌려준다", () => {
    expect(loginErrorMessage("SIGNUP_SESSION_EXPIRED")).toContain("가입 시간이 지났어요");
    expect(loginErrorMessage("SESSION_EXPIRED")).toContain("로그인이 만료됐어요");
    expect(loginErrorMessage("OAUTH_CANCELLED")).toContain("취소");
  });

  it("모르는 사유는 일반 인증 실패로 안내한다", () => {
    expect(loginErrorMessage("SOMETHING_NEW")).toBe(loginErrorMessage("OAUTH_FAILED"));
  });
});
