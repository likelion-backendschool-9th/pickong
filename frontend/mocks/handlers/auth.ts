import { randomUUID } from "node:crypto";
import { db, ME_ID } from "../db.ts";
import { HttpError, html, redirect, withCookies, type Ctx, type Route } from "../router.ts";

/*
 * 소셜 로그인(카카오 · Google) · 최초 가입 온보딩 · 토큰 mock.
 *
 * 실제 제공자 대신 이 서버가 동의 화면을 흉내 냅니다(GET /api/v1/auth/oauth2/authorization/:provider).
 * 나머지는 실제 백엔드와 같은 규칙으로 동작합니다.
 * - state를 쿠키와 서버에 함께 저장하고 콜백에서 비교합니다 (로그인 CSRF 방지).
 * - 처음 온 소셜 계정은 임시 가입 세션 쿠키만 받고, 온보딩을 마쳐야 Access · Refresh Token 쿠키를 받습니다.
 * - Refresh Token은 쓸 때마다 바뀌고, 이미 바뀐 토큰이 다시 오면 같은 family 전체를 폐기합니다.
 *
 * mock에는 ‘나’(서연)가 한 명뿐이라, 새로 가입하거나 다른 계정으로 로그인해도 /api/v1/me는 서연을 돌려줍니다.
 * 피드 · 지도 등 다른 mock API는 지금처럼 로그인 없이도 응답합니다. 로그인이 필요한 mock API는 로그인 수단 관리뿐입니다.
 */

type Provider = "KAKAO" | "GOOGLE";
const PROVIDER_BY_SLUG: Record<string, Provider> = { kakao: "KAKAO", google: "GOOGLE" };
const LABEL: Record<Provider, string> = { KAKAO: "카카오", GOOGLE: "Google" };
const slug = (p: Provider) => p.toLowerCase();

/** 쿠키 이름 · 경로. 실제 백엔드와 같습니다(백엔드는 HTTPS 요청이면 Secure가 붙음). */
const COOKIE = {
  access: { name: "pickong_at", path: "/" },
  refresh: { name: "pickong_rt", path: "/api/v1/auth" },
  signup: { name: "pickong_signup", path: "/api/v1/auth" },
  oauthState: { name: "pickong_oauth_state", path: "/api/v1/auth/oauth2" },
} as const;
type CookieKey = keyof typeof COOKIE;

/** Access Token 수명: 재발급 흐름을 직접 확인할 수 있도록 짧게 둡니다. MOCK_ACCESS_TTL_SEC로 바꿀 수 있습니다. */
const ACCESS_TTL_SEC = Number(process.env.MOCK_ACCESS_TTL_SEC ?? 120);
const REFRESH_TTL_SEC = 14 * 24 * 60 * 60;
const SIGNUP_TTL_SEC = 30 * 60;
const OAUTH_STATE_TTL_SEC = 10 * 60;
const LINK_TTL_SEC = 5 * 60;

const setCookie = (key: CookieKey, value: string, maxAgeSec: number) =>
  `${COOKIE[key].name}=${encodeURIComponent(value)}; Path=${COOKIE[key].path}; Max-Age=${maxAgeSec}; HttpOnly; SameSite=Lax`;
const clearCookie = (key: CookieKey) => `${COOKIE[key].name}=; Path=${COOKIE[key].path}; Max-Age=0; HttpOnly; SameSite=Lax`;
const readCookie = (ctx: Ctx, key: CookieKey) => ctx.cookies[COOKIE[key].name];

const now = () => Date.now();
const ago = (days: number) => new Date(now() - days * 86_400_000).toISOString();
const token = () => randomUUID().replaceAll("-", "");

// ── 메모리 상태 ─────────────────────────────────────────

interface SocialAccountRow {
  userId: string;
  provider: Provider;
  providerUserId: string;
  email: string | null;
  connectedAt: string;
}

/** (provider, providerUserId)는 하나의 찜콩 계정에만 연결됩니다. */
const socialAccounts: SocialAccountRow[] = [
  { userId: ME_ID, provider: "KAKAO", providerUserId: "kakao-seoyeon", email: "seoyeon@kakao.mock", connectedAt: ago(40) },
  // 다른 회원(지민)에 연결된 Google 계정: 중복 연결 거절을 확인하는 용도
  { userId: "u-jimin", provider: "GOOGLE", providerUserId: "google-jimin", email: "jimin@gmail.mock", connectedAt: ago(20) },
];

const accessTokens = new Map<string, { userId: string; expiresAt: number }>();
const refreshTokens = new Map<string, { userId: string; family: string; expiresAt: number; rotated: boolean }>();
const pendingSignups = new Map<
  string,
  { provider: Provider; providerUserId: string; name: string | null; profileImageUrl: string | null; email: string | null; expiresAt: number }
>();
const oauthStates = new Map<string, { provider: Provider; mode: "login" | "link"; userId: string | null; expiresAt: number }>();
const linkTokens = new Map<string, { userId: string; provider: Provider; expiresAt: number }>();

/** 새 Access · Refresh Token을 발급하고 쿠키로 내려줍니다. family를 넘기면 같은 로그인 세션의 다음 토큰입니다. */
function issueSession(userId: string, family: string = randomUUID()): string[] {
  const access = token();
  const refresh = token();
  accessTokens.set(access, { userId, expiresAt: now() + ACCESS_TTL_SEC * 1000 });
  refreshTokens.set(refresh, { userId, family, expiresAt: now() + REFRESH_TTL_SEC * 1000, rotated: false });
  return [setCookie("access", access, REFRESH_TTL_SEC), setCookie("refresh", refresh, REFRESH_TTL_SEC)];
}

function revokeFamily(family: string) {
  for (const [key, row] of refreshTokens) if (row.family === family) refreshTokens.delete(key);
}

const clearSessionCookies = () => [clearCookie("access"), clearCookie("refresh")];

function currentUserId(ctx: Ctx): string | null {
  const row = accessTokens.get(readCookie(ctx, "access") ?? "");
  return row && row.expiresAt > now() ? row.userId : null;
}

/** 로그인 필요: Access Token이 없거나 만료되면 401 → 프론트가 재발급 후 다시 요청합니다. */
function requireUser(ctx: Ctx): string {
  const userId = currentUserId(ctx);
  if (!userId) throw new HttpError(401, "UNAUTHORIZED", "로그인이 필요해요.");
  return userId;
}

function providerOf(ctx: Ctx): Provider {
  const provider = PROVIDER_BY_SLUG[ctx.params.provider];
  if (!provider) throw new HttpError(404, "NOT_FOUND", "지원하지 않는 로그인 방식이에요.");
  return provider;
}

// ── 닉네임 = 사용자 ID 규칙 (프론트 signup.ts와 같음): 영문으로 시작, 영문 소문자 · 숫자 · _ · . 3~20자 ──

export const USERNAME_PATTERN = /^(?!.*\.\.)(?!.*\.$)[a-z][a-z0-9._]{2,19}$/;
/** 회원이 아닌데도 쓸 수 없는 닉네임 */
const RESERVED_USERNAMES = ["admin", "pickong", "jjimkong", "settings", "login", "signup", "api", "me"];
/** exceptUserId: 프로필 편집에서 지금 내 닉네임은 중복으로 보지 않습니다. */
export const isUsernameTaken = (username: string, exceptUserId?: string) =>
  RESERVED_USERNAMES.includes(username) || db.users.some((u) => u.id !== exceptUserId && u.username.toLowerCase() === username);

// ── 가짜 소셜 동의 화면 ─────────────────────────────────

function consentPage(provider: Provider, mode: "login" | "link", state: string) {
  const callback = (params: Record<string, string>) =>
    `/api/v1/auth/oauth2/callback/${slug(provider)}?${new URLSearchParams({ state, ...params })}`;
  const owner = (userId: string) => db.users.find((u) => u.id === userId)?.nickname ?? userId;
  const existing = socialAccounts
    .filter((a) => a.provider === provider)
    .map((a) => ({
      href: callback({ code: `id:${a.providerUserId}` }),
      title: a.email ?? a.providerUserId,
      desc: `찜콩 회원 ${owner(a.userId)}님에게 연결된 계정${a.userId === ME_ID ? " (나)" : ""}`,
    }));
  const options = [
    ...existing,
    { href: callback({ code: "new" }), title: `새 ${LABEL[provider]} 계정`, desc: "찜콩에 처음 오는 계정 → 온보딩으로 이동" },
    {
      href: callback({ code: "email-taken" }),
      title: "다른 회원과 이메일이 같은 계정",
      desc: "처음 오는 계정인데 인증된 이메일이 이미 다른 찜콩 회원 것 → EMAIL_ALREADY_REGISTERED",
    },
    ...(mode === "login"
      ? [{ href: callback({ code: "suspended" }), title: "정지된 회원의 계정", desc: "이용할 수 없는 계정 → ACCOUNT_UNAVAILABLE" }]
      : []),
    { href: callback({ error: "access_denied" }), title: "취소", desc: "동의 화면에서 취소한 경우" },
    { href: `/api/v1/auth/oauth2/callback/${slug(provider)}?code=new&state=forged`, title: "state 위조", desc: "로그인 CSRF 공격처럼 state가 다른 콜백" },
  ];
  const color = provider === "KAKAO" ? "#fee500" : "#ffffff";
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>mock ${LABEL[provider]} 로그인</title>
<style>body{margin:0;font-family:system-ui,sans-serif;background:#f5f6f3;color:#20251f}main{max-width:420px;margin:0 auto;padding:32px 20px}
h1{font-size:20px;margin:0 0 4px}p{color:#5d6357;font-size:14px;margin:0 0 20px}a{display:block;text-decoration:none;color:inherit;background:#fff;border:1px solid #e9ece6;border-radius:14px;padding:14px 16px;margin-bottom:10px}
a b{display:block;font-size:15px}a span{font-size:13px;color:#5d6357}.badge{display:inline-block;background:${color};border:1px solid #e9ece6;border-radius:999px;padding:2px 10px;font-size:12px;margin-bottom:12px}</style></head>
<body><main><span class="badge">mock ${LABEL[provider]}</span><h1>${mode === "link" ? "연결할 계정을 고르세요" : "로그인할 계정을 고르세요"}</h1>
<p>개발용 가짜 동의 화면이에요. 실제 ${LABEL[provider]} 인증은 백엔드 연결 후 확인합니다.</p>
${options.map((o) => `<a href="${o.href}"><b>${o.title}</b><span>${o.desc}</span></a>`).join("\n")}
</main></body></html>`;
}

/** 새 소셜 계정의 프로필: 매번 다른 사용자처럼 보이도록 id를 새로 만듭니다. */
function newIdentity(provider: Provider) {
  const id = randomUUID().slice(0, 8);
  return {
    providerUserId: `${slug(provider)}-${id}`,
    name: provider === "KAKAO" ? "콩이" : "Pickong Tester",
    email: provider === "KAKAO" ? null : `tester${id.replace(/\d/g, "")}@gmail.mock`,
    profileImageUrl: "/images/photos/park.jpg",
  };
}

// ── 라우트 ─────────────────────────────────────────────

export const authRoutes: Route[] = [
  {
    /**
     * 소셜 로그인 시작(페이지 이동). state를 만들어 쿠키와 서버에 저장하고 동의 화면으로 보냅니다.
     * ?link=<1회용 토큰>이 있으면 로그인한 회원의 계정 추가 연결입니다.
     */
    method: "GET",
    path: "/api/v1/auth/oauth2/authorization/:provider",
    delay: 0,
    handler: (ctx) => {
      const provider = providerOf(ctx);
      const linkToken = ctx.query.get("link");
      let mode: "login" | "link" = "login";
      let userId: string | null = null;
      if (linkToken) {
        const link = linkTokens.get(linkToken);
        linkTokens.delete(linkToken);
        if (!link || link.provider !== provider || link.expiresAt < now()) return redirect("/settings/login-methods?error=LINK_EXPIRED");
        mode = "link";
        userId = link.userId;
      }
      const state = token();
      oauthStates.set(state, { provider, mode, userId, expiresAt: now() + OAUTH_STATE_TTL_SEC * 1000 });
      return html(consentPage(provider, mode, state), [setCookie("oauthState", state, OAUTH_STATE_TTL_SEC)]);
    },
  },
  {
    /** 제공자 콜백. 기존 회원 → 로그인 후 피드, 신규 → 임시 가입 세션 후 온보딩, 연결 → 로그인 수단 관리 */
    method: "GET",
    path: "/api/v1/auth/oauth2/callback/:provider",
    delay: 0,
    handler: (ctx) => {
      const provider = providerOf(ctx);
      const state = ctx.query.get("state") ?? "";
      const saved = oauthStates.get(state);
      oauthStates.delete(state);
      const cookies = [clearCookie("oauthState")];
      const fail = (mode: "login" | "link", code: string) =>
        redirect(mode === "link" ? `/settings/login-methods?error=${code}` : `/login?error=${code}`, cookies);

      // state가 없거나, 이 브라우저가 시작한 요청이 아니면(쿠키 불일치) 거절합니다.
      if (!saved || saved.expiresAt < now() || saved.provider !== provider || readCookie(ctx, "oauthState") !== state) {
        return fail(saved?.mode ?? "login", "OAUTH_FAILED");
      }
      if (ctx.query.get("error")) return fail(saved.mode, "OAUTH_CANCELLED");

      const code = ctx.query.get("code") ?? "";
      if (code === "suspended" && saved.mode === "login") return fail("login", "ACCOUNT_UNAVAILABLE");
      if (code === "email-taken") {
        // 백엔드: 처음 오는 소셜 계정의 인증된 이메일이 다른 회원 것이면 막습니다(1인 1계정, 자동 연결 없음).
        const owner = socialAccounts.find((a) => a.email && a.userId !== saved.userId);
        if (owner) {
          return saved.mode === "link"
            ? fail("link", "EMAIL_ALREADY_REGISTERED")
            : redirect(`/login?error=EMAIL_ALREADY_REGISTERED&provider=${slug(owner.provider)}`, cookies);
        }
      }
      const known = code.startsWith("id:") ? socialAccounts.find((a) => a.provider === provider && a.providerUserId === code.slice(3)) : undefined;
      const identity = known
        ? { providerUserId: known.providerUserId, name: null, email: known.email, profileImageUrl: null }
        : newIdentity(provider);
      const linked = socialAccounts.find((a) => a.provider === provider && a.providerUserId === identity.providerUserId);

      if (saved.mode === "link") {
        const userId = saved.userId!;
        if (linked && linked.userId !== userId) return fail("link", "SOCIAL_ACCOUNT_ALREADY_LINKED");
        if (!linked) {
          if (socialAccounts.some((a) => a.userId === userId && a.provider === provider)) return fail("link", "PROVIDER_ALREADY_LINKED");
          socialAccounts.push({ userId, provider, providerUserId: identity.providerUserId, email: identity.email, connectedAt: new Date().toISOString() });
        }
        return redirect(`/settings/login-methods?linked=${slug(provider)}`, cookies);
      }

      if (linked) {
        return redirect("/feed", [...cookies, clearCookie("signup"), ...issueSession(linked.userId)]);
      }
      const signupId = token();
      pendingSignups.set(signupId, { provider, ...identity, expiresAt: now() + SIGNUP_TTL_SEC * 1000 });
      // 온보딩 전에는 정식 토큰을 주지 않고, 남아 있던 다른 계정의 세션도 지웁니다.
      return redirect("/signup", [...cookies, ...clearSessionCookies(), setCookie("signup", signupId, SIGNUP_TTL_SEC)]);
    },
  },

  // ── 최초 가입 온보딩 ──────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/auth/signup",
    handler: (ctx) => {
      const pending = pendingSignups.get(readCookie(ctx, "signup") ?? "");
      if (pending && pending.expiresAt > now()) {
        return { provider: pending.provider, name: pending.name, profileImageUrl: pending.profileImageUrl, email: pending.email };
      }
      if (currentUserId(ctx)) throw new HttpError(409, "ALREADY_REGISTERED", "이미 가입을 마쳤어요.");
      throw new HttpError(401, "SIGNUP_SESSION_EXPIRED", "가입 시간이 지났어요. 처음부터 다시 진행해 주세요.", [clearCookie("signup")]);
    },
  },
  {
    method: "POST",
    path: "/api/v1/auth/signup",
    delay: 500,
    handler: (ctx) => {
      const signupId = readCookie(ctx, "signup") ?? "";
      const pending = pendingSignups.get(signupId);
      if (!pending || pending.expiresAt < now()) {
        // 다른 탭에서 이미 가입을 마친 경우
        if (currentUserId(ctx)) throw new HttpError(409, "ALREADY_REGISTERED", "이미 가입을 마쳤어요.");
        throw new HttpError(401, "SIGNUP_SESSION_EXPIRED", "가입 시간이 지났어요. 처음부터 다시 진행해 주세요.", [clearCookie("signup")]);
      }

      // 닉네임 = 사용자 ID(username, 중복 불가), 이름(name, 중복 가능 · 없으면 null)
      const username = String(ctx.body?.username ?? "").trim().toLowerCase();
      const name = typeof ctx.body?.name === "string" ? ctx.body.name.trim() || null : null;
      const agreed: string[] = Array.isArray(ctx.body?.agreedTerms) ? ctx.body.agreedTerms : [];
      if (!USERNAME_PATTERN.test(username)) throw new HttpError(400, "INVALID_USERNAME", "닉네임 형식을 확인해 주세요.");
      if (name && [...name].length > 20) throw new HttpError(400, "INVALID_NAME", "이름은 20자까지 쓸 수 있어요.");
      if (!agreed.includes("SERVICE") || !agreed.includes("PRIVACY")) {
        throw new HttpError(400, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 동의해 주세요.");
      }
      if (isUsernameTaken(username)) throw new HttpError(409, "USERNAME_TAKEN", "이미 사용 중인 닉네임이에요.");
      if (socialAccounts.some((a) => a.provider === pending.provider && a.providerUserId === pending.providerUserId)) {
        throw new HttpError(409, "SOCIAL_ACCOUNT_ALREADY_LINKED", "이미 가입된 소셜 계정이에요. 다시 로그인해 주세요.");
      }

      const userId = `u-${username}`;
      db.users.push({
        id: userId,
        username,
        name,
        nickname: name ?? username,
        profileImageUrl: ctx.body?.useSocialProfileImage ? pending.profileImageUrl : null,
        bio: "",
        region: "",
        postCount: 0,
        followerCount: 0,
        followingCount: 0,
      });
      socialAccounts.push({
        userId,
        provider: pending.provider,
        providerUserId: pending.providerUserId,
        email: pending.email,
        connectedAt: new Date().toISOString(),
      });
      pendingSignups.delete(signupId);
      return withCookies({ username }, [clearCookie("signup"), ...issueSession(userId)]);
    },
  },
  {
    /** 가입 그만두기 */
    method: "DELETE",
    path: "/api/v1/auth/signup",
    handler: (ctx) => {
      pendingSignups.delete(readCookie(ctx, "signup") ?? "");
      return withCookies(undefined, [clearCookie("signup")]);
    },
  },
  {
    /** 닉네임(= 사용자 ID) 중복 확인. 대소문자를 무시하고 비교합니다. */
    method: "GET",
    path: "/api/v1/auth/username-availability",
    delay: 300,
    handler: ({ query }) => {
      const username = (query.get("username") ?? "").trim().toLowerCase();
      if (!USERNAME_PATTERN.test(username)) throw new HttpError(400, "INVALID_USERNAME", "닉네임 형식을 확인해 주세요.");
      return { available: !isUsernameTaken(username) };
    },
  },

  // ── 토큰 ──────────────────────────────────────────────
  {
    /** Access Token 재발급 + Refresh Token 로테이션. 이미 바뀐 토큰이 다시 오면 family 전체를 폐기합니다. */
    method: "POST",
    path: "/api/v1/auth/refresh",
    handler: (ctx) => {
      const presented = readCookie(ctx, "refresh") ?? "";
      const row = refreshTokens.get(presented);
      if (!row || row.expiresAt < now()) {
        throw new HttpError(401, "INVALID_REFRESH_TOKEN", "로그인이 필요해요.", clearSessionCookies());
      }
      if (row.rotated) {
        revokeFamily(row.family);
        throw new HttpError(401, "REFRESH_TOKEN_REUSED", "보안을 위해 로그아웃됐어요. 다시 로그인해 주세요.", clearSessionCookies());
      }
      row.rotated = true;
      return withCookies(undefined, issueSession(row.userId, row.family));
    },
  },
  {
    /** 로그아웃: 이 로그인 세션(family)의 Refresh Token을 모두 폐기하고 쿠키를 지웁니다. */
    method: "POST",
    path: "/api/v1/auth/logout",
    handler: (ctx) => {
      const row = refreshTokens.get(readCookie(ctx, "refresh") ?? "");
      if (row) revokeFamily(row.family);
      accessTokens.delete(readCookie(ctx, "access") ?? "");
      return withCookies(undefined, clearSessionCookies());
    },
  },

  // ── 로그인 수단 관리 (로그인 필요) ──────────────────────────
  {
    method: "GET",
    path: "/api/v1/me/social-accounts",
    handler: (ctx) => {
      const userId = requireUser(ctx);
      // 연결한 순서대로. 가장 먼저 연결된 것이 가입할 때 사용한 수단이라 해제할 수 없습니다(백엔드와 같은 규칙).
      return socialAccounts
        .filter((a) => a.userId === userId)
        .map(({ provider, email, connectedAt }, i) => ({ provider, email, connectedAt, canUnlink: i > 0 }));
    },
  },
  {
    /** 계정 추가 연결 시작: 이 회원 전용 1회용 연결 주소를 만들어 줍니다. */
    method: "POST",
    path: "/api/v1/me/social-accounts/:provider",
    handler: (ctx) => {
      const userId = requireUser(ctx);
      const provider = providerOf(ctx);
      if (socialAccounts.some((a) => a.userId === userId && a.provider === provider)) {
        throw new HttpError(409, "PROVIDER_ALREADY_LINKED", "이 로그인 수단은 이미 연결돼 있어요.");
      }
      const link = token();
      linkTokens.set(link, { userId, provider, expiresAt: now() + LINK_TTL_SEC * 1000 });
      return { authorizationUrl: `/api/v1/auth/oauth2/authorization/${slug(provider)}?link=${link}` };
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/me/social-accounts/:provider",
    handler: (ctx) => {
      const userId = requireUser(ctx);
      const provider = providerOf(ctx);
      const mine = socialAccounts.filter((a) => a.userId === userId);
      const target = mine.find((a) => a.provider === provider);
      if (!target) throw new HttpError(404, "SOCIAL_ACCOUNT_NOT_LINKED", "연결되지 않은 로그인 수단이에요.");
      if (target === mine[0]) throw new HttpError(409, "SIGNUP_METHOD_CANNOT_BE_UNLINKED", "가입할 때 사용한 로그인 수단은 해제할 수 없어요.");
      socialAccounts.splice(socialAccounts.indexOf(target), 1);
      return undefined;
    },
  },
];
