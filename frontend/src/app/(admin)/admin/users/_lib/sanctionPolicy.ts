import type { SanctionType, UserStatus } from "../../_lib/models";

export function canSanction(status: UserStatus, type: SanctionType, restriction: SanctionType | null = null): boolean {
  if (status === "WITHDRAWN" || status === "WITHDRAWN_PENDING") return false;
  if (type === "WARNING") return true;
  if (status === "BANNED") return false;
  // 만료 정리가 아직 안 된 SUSPENDED 계정은 유효한 제재 목록으로 판단하고, 최종 검증은 서버가 합니다.
  return restriction === null || (restriction !== "BAN" && type === "BAN");
}
