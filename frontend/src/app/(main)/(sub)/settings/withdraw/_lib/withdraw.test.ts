import { describe, expect, it } from "vitest";
import { withdrawAction } from "./withdraw";

describe("withdrawAction", () => {
  it("사유가 없거나 '기타'인데 내용이 비어 있으면 버튼을 끈다", () => {
    expect(withdrawAction("", "", true)).toBe("DISABLED");
    expect(withdrawAction("ETC", "   ", true)).toBe("DISABLED");
  });

  it("사유를 골랐는데 확인 체크가 없으면 누를 수 있지만 체크를 알려야 한다", () => {
    expect(withdrawAction("RARELY_USED", "", false)).toBe("NEEDS_CONFIRM");
    expect(withdrawAction("ETC", "더 이상 안 써요", false)).toBe("NEEDS_CONFIRM");
  });

  it("사유와 확인 체크가 모두 있으면 탈퇴할 수 있다", () => {
    expect(withdrawAction("PRIVACY", "", true)).toBe("READY");
  });
});
