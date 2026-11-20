import { db, ME_ID, userDto } from "../db.ts";
import { HttpError, type Route } from "../router.ts";

/**
 * 개발용 테스트 계정. 로그인하면 mock의 ‘나’(ME_ID) 사용자로 들어갑니다.
 * 실제 백엔드에는 없는 계정입니다.
 */
const TEST_ACCOUNTS = [{ email: "test", password: "123123123", userId: ME_ID }];

export const authRoutes: Route[] = [
  {
    method: "POST",
    path: "/api/auth/login",
    handler: ({ body }) => {
      const email = typeof body?.email === "string" ? body.email.trim() : "";
      const password = typeof body?.password === "string" ? body.password : "";
      const account = TEST_ACCOUNTS.find((a) => a.email === email && a.password === password);
      if (!account) throw new HttpError(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요.");
      return userDto(db.users.find((u) => u.id === account.userId)!);
    },
  },
];
