import { db, ME_ID, userDto } from "../db.ts";
import { HttpError, type Route } from "../router.ts";

/**
 * 개발용 테스트 계정. 실제 백엔드에는 없는 계정입니다.
 * - test: mock의 ‘나’(ME_ID) 회원으로 들어갑니다.
 * - admin: 회원과 별개인 관리자 계정으로, 로그인하면 관리 페이지로 이동합니다.
 */
const TEST_ACCOUNTS = [
  { email: "test", password: "123123123", role: "USER" as const },
  { email: "admin", password: "123123123", role: "ADMIN" as const },
];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;

/** 비밀번호 재설정 진행 중인 요청: 인증을 마치면 token이 생깁니다. */
const resetRequests = new Map<string, { email: string; verified: boolean }>();
let resetSeq = 0;

const mask = (email: string) => {
  const [id, domain] = email.split("@");
  return `${id.slice(0, 2)}${"*".repeat(Math.max(1, id.length - 2))}@${domain}`;
};

export const authRoutes: Route[] = [
  {
    method: "POST",
    path: "/api/auth/login",
    handler: ({ body }) => {
      const email = typeof body?.email === "string" ? body.email.trim() : "";
      const password = typeof body?.password === "string" ? body.password : "";
      const account = TEST_ACCOUNTS.find((a) => a.email === email && a.password === password);
      if (!account) throw new HttpError(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요.");
      if (account.role === "ADMIN") return { role: "ADMIN", nickname: "관리자" };
      const me = userDto(db.users.find((u) => u.id === ME_ID)!);
      return { role: "USER", nickname: me.nickname };
    },
  },
  { method: "POST", path: "/api/auth/logout", handler: () => undefined },

  // ── 비밀번호 재설정: 이메일 → 인증번호 → 새 비밀번호 ──────────
  {
    method: "POST",
    path: "/api/auth/password-reset",
    delay: 500,
    handler: ({ body }) => {
      const email = String(body?.email ?? "").trim();
      if (!EMAIL_PATTERN.test(email)) throw new HttpError(400, "INVALID_INPUT", "이메일 형식을 확인해 주세요.");
      const requestId = `pr-${++resetSeq}`;
      resetRequests.set(requestId, { email, verified: false });
      // 가입하지 않은 이메일이어도 같은 응답을 줘서 가입 여부를 알 수 없게 합니다.
      return { requestId, sentTo: mask(email) };
    },
  },
  {
    /** mock: 6자리 숫자면 통과합니다. */
    method: "POST",
    path: "/api/auth/password-reset/:requestId/verify",
    handler: ({ params, body }) => {
      const req = resetRequests.get(params.requestId);
      if (!req) throw new HttpError(404, "NOT_FOUND", "인증 요청이 만료됐어요. 처음부터 다시 시도해 주세요.");
      if (!/^\d{6}$/.test(String(body?.code ?? ""))) throw new HttpError(400, "INVALID_CODE", "인증번호가 맞지 않아요.");
      req.verified = true;
      return undefined;
    },
  },
  {
    method: "POST",
    path: "/api/auth/password-reset/:requestId/confirm",
    handler: ({ params, body }) => {
      const req = resetRequests.get(params.requestId);
      if (!req?.verified) throw new HttpError(400, "NOT_VERIFIED", "이메일 인증을 먼저 완료해 주세요.");
      if (!PASSWORD_PATTERN.test(String(body?.password ?? ""))) {
        throw new HttpError(400, "INVALID_INPUT", "영문·숫자를 포함한 8자 이상으로 설정해 주세요.");
      }
      resetRequests.delete(params.requestId);
      return undefined;
    },
  },
];
