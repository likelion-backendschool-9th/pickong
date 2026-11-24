/** Access Token 재발급까지 실패해 다시 로그인해야 하는 상태. QueryClient가 받으면 로그인 화면으로 보냅니다. */
export const SESSION_EXPIRED = "SESSION_EXPIRED";

/** 재동의가 필요한 필수 약관에 아직 동의하지 않음(서버 403). QueryClient가 받으면 재동의 화면으로 보냅니다. */
export const TERMS_RECONSENT_REQUIRED = "TERMS_RECONSENT_REQUIRED";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }

  get isNotFound() {
    return this.status === 404;
  }
}

/** 화면에 보여줄 오류 문구 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "잠시 후 다시 시도해 주세요.";
}
