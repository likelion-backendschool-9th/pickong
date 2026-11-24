import { describe, expect, it } from "vitest";
import {
  NO_AGREEMENTS,
  agreedTermsTypes,
  hasRequiredAgreements,
  isAllAgreed,
  previousStep,
  stepForSignupError,
  stepNumber,
  suggestedNickname,
  toggleAllAgreements,
} from "./signup";

describe("온보딩 단계", () => {
  it("닉네임 → 프로필 → 약관 순서로 번호를 매긴다", () => {
    expect(stepNumber("nickname")).toBe(1);
    expect(stepNumber("profile")).toBe(2);
    expect(stepNumber("terms")).toBe(3);
  });

  it("이전 단계로 돌아가고, 첫 단계에서는 null을 돌려준다", () => {
    expect(previousStep("terms")).toBe("profile");
    expect(previousStep("profile")).toBe("nickname");
    expect(previousStep("nickname")).toBeNull();
  });
});

describe("suggestedNickname", () => {
  it("이메일 앞부분이 규칙에 맞으면 기본값으로 쓴다", () => {
    expect(suggestedNickname("Seo.Yeon@gmail.com")).toBe("seo.yeon");
  });

  it("규칙에 맞지 않거나 이메일이 없으면 빈칸", () => {
    expect(suggestedNickname("12345@kakao.com")).toBe("");
    expect(suggestedNickname("a-b-c@gmail.com")).toBe("");
    expect(suggestedNickname(null)).toBe("");
  });
});

describe("약관 동의", () => {
  it("필수 항목만 동의해도 진행할 수 있다", () => {
    const agreements = { terms: true, privacy: true, marketing: false };
    expect(hasRequiredAgreements(agreements)).toBe(true);
    expect(isAllAgreed(agreements)).toBe(false);
  });

  it("필수 항목이 빠지면 진행할 수 없다", () => {
    expect(hasRequiredAgreements({ terms: true, privacy: false, marketing: true })).toBe(false);
    expect(hasRequiredAgreements(NO_AGREEMENTS)).toBe(false);
  });

  it("모두 동의는 일부만 켜져 있으면 전부 켜고, 전부 켜져 있으면 전부 끈다", () => {
    const all = toggleAllAgreements({ terms: true, privacy: false, marketing: false });
    expect(all).toEqual({ terms: true, privacy: true, marketing: true });
    expect(toggleAllAgreements(all)).toEqual(NO_AGREEMENTS);
  });

  it("가입 요청에는 동의한 약관의 API 값만 담는다", () => {
    expect(agreedTermsTypes({ terms: true, privacy: true, marketing: false })).toEqual(["SERVICE", "PRIVACY"]);
  });
});

describe("stepForSignupError", () => {
  it("닉네임 · 이름 · 약관 오류를 각 단계로 돌려보낸다", () => {
    expect(stepForSignupError("USERNAME_TAKEN")).toBe("nickname");
    expect(stepForSignupError("INVALID_USERNAME")).toBe("nickname");
    expect(stepForSignupError("INVALID_NAME")).toBe("profile");
    expect(stepForSignupError("REQUIRED_TERMS_NOT_AGREED")).toBe("terms");
  });

  it("단계와 관계없는 오류는 null", () => {
    expect(stepForSignupError("SIGNUP_SESSION_EXPIRED")).toBeNull();
  });
});
