import { describe, expect, it } from "vitest";
import { accountDigits, accountNumberError, channelUrlError, introError, netAmount } from "./creator";

describe("크리에이터 신청 검증", () => {
  it("소개는 공백을 빼고 10자 이상", () => {
    expect(introError("   짧아요   ")).not.toBeNull();
    expect(introError("성수 카페를 3년째 다니고 있어요")).toBeNull();
  });

  it("채널 주소는 비워 두거나 http(s) 주소", () => {
    expect(channelUrlError("")).toBeNull();
    expect(channelUrlError("instagram.com/me")).not.toBeNull();
    expect(channelUrlError("https://instagram.com/me")).toBeNull();
  });
});

describe("정산 계좌", () => {
  it("숫자만 남기고 14자리에서 자른다", () => {
    expect(accountDigits("123-456-789012-345")).toBe("12345678901234");
  });

  it("10~14자리만 통과", () => {
    expect(accountNumberError("")).toBe("계좌번호를 입력해 주세요.");
    expect(accountNumberError("123-456")).not.toBeNull();
    expect(accountNumberError("123-456-7890")).toBeNull();
  });
});

describe("netAmount", () => {
  it("수수료(반올림)를 뺀 금액", () => {
    expect(netAmount(1900, 0.2)).toBe(1520);
    expect(netAmount(999, 0.2)).toBe(799);
  });
});
