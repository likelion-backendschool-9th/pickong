import { describe, expect, it } from "vitest";
import type { SanctionType, UserStatus } from "../../_lib/models";
import { canSanction } from "./sanctionPolicy";

describe("추가 제재 선택", () => {
  it.each<[UserStatus, SanctionType, boolean]>([
    ["ACTIVE", "WARNING", true], ["ACTIVE", "SUSPEND_7", true], ["ACTIVE", "SUSPEND_30", true], ["ACTIVE", "BAN", true],
    ["SUSPENDED", "WARNING", true], ["SUSPENDED", "SUSPEND_7", false], ["SUSPENDED", "SUSPEND_30", false], ["SUSPENDED", "BAN", true],
    ["BANNED", "WARNING", true], ["BANNED", "SUSPEND_7", false], ["BANNED", "BAN", false],
    ["WITHDRAWN_PENDING", "WARNING", false], ["WITHDRAWN", "BAN", false],
  ])("%s에서 %s 선택 가능 여부는 %s", (status, type, expected) => {
    expect(canSanction(status, type, status === "SUSPENDED" ? "SUSPEND_7" : null)).toBe(expected);
  });
  it("만료 이력이 정리되지 않은 계정도 유효한 정지가 없으면 새 기간 정지를 요청할 수 있다", () => {
    expect(canSanction("SUSPENDED", "SUSPEND_30", null)).toBe(true);
  });
});
