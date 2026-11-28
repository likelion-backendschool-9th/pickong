/**
 * 공백 판정은 관리자 사유 입력과 같이 Unicode 공백을 비움으로 봅니다.
 * Mock 서버가 Node에서 직접 불러도 경로 별칭이 없도록 이 파일만 의존합니다.
 */
const UNICODE_TRIM = /^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]+$/gu;

export function normalizeAdminPlaceText(value: string): string {
  return value.replace(UNICODE_TRIM, "");
}

/** 이미 저장된 주소만 요청에 실을 수 있습니다. blob·data 주소는 브라우저 세션에만 있습니다. */
export function isStoredImageUrl(url: string): boolean {
  return url.length > 0 && !url.startsWith("blob:") && !url.startsWith("data:");
}
