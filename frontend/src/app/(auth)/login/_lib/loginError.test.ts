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
    expect(loginErrorMessage("RESTORE_SESSION_EXPIRED")).toBe("다시 로그인하면 탈퇴를 취소할 수 있어요.");
    expect(loginErrorMessage("OAUTH_CANCELLED")).toContain("취소");
  });

  it("이용할 수 없는 계정(정지 · 탈퇴)은 전용 문구로 안내한다", () => {
    expect(loginErrorMessage("ACCOUNT_UNAVAILABLE")).toContain("이용할 수 없는 계정");
  });

  it("이미 가입된 이메일이면 가입한 수단으로 로그인하라고 알려 준다", () => {
    expect(loginErrorMessage("EMAIL_ALREADY_REGISTERED", "kakao")).toBe("이미 카카오로 가입된 이메일이에요. 카카오로 로그인해 주세요.");
    expect(loginErrorMessage("EMAIL_ALREADY_REGISTERED", "google")).toContain("Google로 로그인");
    // 수단을 모르면 일반 안내
    expect(loginErrorMessage("EMAIL_ALREADY_REGISTERED", null)).toContain("처음 가입한 수단");
    expect(loginErrorMessage("EMAIL_ALREADY_REGISTERED", "naver")).toContain("처음 가입한 수단");
  });

  it("모르는 사유는 일반 인증 실패로 안내한다", () => {
    expect(loginErrorMessage("SOMETHING_NEW")).toBe(loginErrorMessage("OAUTH_FAILED"));
  });
});
