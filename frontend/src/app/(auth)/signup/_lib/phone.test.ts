import { describe, expect, it } from "vitest";
import { formatPhone, isValidPhone } from "./phone";

describe("formatPhone", () => {
  it("입력 길이에 맞춰 하이픈을 넣는다", () => {
    expect(formatPhone("010")).toBe("010");
    expect(formatPhone("0101234")).toBe("010-1234");
    expect(formatPhone("01012345678")).toBe("010-1234-5678");
    expect(formatPhone("0111234567")).toBe("011-123-4567");
  });

  it("숫자 외 문자는 지우고 11자리에서 자른다", () => {
    expect(formatPhone("010 1234 5678 9")).toBe("010-1234-5678");
    expect(formatPhone("010-12ab34")).toBe("010-1234");
  });
});

describe("isValidPhone", () => {
  it("휴대폰 번호 형식만 통과시킨다", () => {
    expect(isValidPhone("010-1234-5678")).toBe(true);
    expect(isValidPhone("011-123-4567")).toBe(true);
    expect(isValidPhone("010-1234-567")).toBe(true);
    expect(isValidPhone("02-123-4567")).toBe(false);
    expect(isValidPhone("010-1234")).toBe(false);
  });
});
