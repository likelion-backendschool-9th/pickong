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
