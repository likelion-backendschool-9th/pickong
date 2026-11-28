import type { IncomingMessage, ServerResponse } from "node:http";

export class HttpError extends Error {
  status: number;
  code: string;
  /** 오류와 함께 보낼 Set-Cookie (예: 폐기된 토큰 쿠키 지우기) */
  cookies: string[];
  constructor(status: number, code: string, message: string, cookies: string[] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.cookies = cookies;
  }
}

/**
 * JSON 대신 직접 만든 응답: 페이지 이동(302), HTML(가짜 소셜 동의 화면), 쿠키를 함께 보내는 JSON.
 * 아래 redirect · html · withCookies로 만듭니다.
 */
export class RawResponse {
  status: number;
  headers: Record<string, string | string[]>;
  body?: string;
  constructor(status: number, headers: Record<string, string | string[]>, body?: string) {
    this.status = status;
    this.headers = headers;
    this.body = body;
  }
}

export const redirect = (location: string, cookies: string[] = []) =>
  new RawResponse(302, { Location: location, "Set-Cookie": cookies });

export const html = (body: string, cookies: string[] = []) =>
  new RawResponse(200, { "Content-Type": "text/html; charset=utf-8", "Set-Cookie": cookies }, body);

/** data가 undefined면 204 */
export const withCookies = (data: unknown, cookies: string[]) =>
  data === undefined
    ? new RawResponse(204, { "Set-Cookie": cookies })
    : new RawResponse(200, { "Content-Type": "application/json; charset=utf-8", "Set-Cookie": cookies }, JSON.stringify(data));

export interface Ctx {
  params: Record<string, string>;
  query: URLSearchParams;
  body: any;
  /** 요청 쿠키 (이름 → 값) */
  cookies: Record<string, string>;
}

export interface Route {
  method: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  path: string;
  /** 응답 지연 (ms). 로딩 상태 확인용 */
  delay?: number;
  handler: (ctx: Ctx) => unknown | Promise<unknown>;
}

const compile = (path: string) =>
  new RegExp("^" + path.replace(/:(\w+)/g, "(?<$1>[^/]+)") + "$");

export function createRouter(routes: Route[]) {
  const compiled = routes.map((r) => ({ ...r, re: compile(r.path) }));

  return async function handle(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? "/", "http://localhost");
    const route = compiled.find((r) => r.method === req.method && r.re.test(url.pathname));

    if (!route) {
      return send(res, 404, { code: "NOT_FOUND", message: `${req.method} ${url.pathname} 없음` });
    }

    try {
      const body = await readBody(req);
      const params = { ...(url.pathname.match(route.re)?.groups ?? {}) };
      await sleep(route.delay ?? 150);
      const data = await route.handler({ params, query: url.searchParams, body, cookies: parseCookies(req.headers.cookie) });
      if (data instanceof RawResponse) {
        res.writeHead(data.status, withoutEmpty(data.headers)).end(data.body);
      } else if (data === undefined) {
        res.writeHead(204).end();
      } else {
        send(res, 200, data);
      }
    } catch (error) {
      if (error instanceof HttpError) {
        send(res, error.status, { code: error.code, message: error.message }, error.cookies);
      } else {
        console.error(error);
        send(res, 500, { code: "INTERNAL", message: "mock 서버 오류" });
      }
    }
  };
}

function send(res: ServerResponse, status: number, data: unknown, cookies: string[] = []) {
  res.writeHead(status, withoutEmpty({ "Content-Type": "application/json; charset=utf-8", "Set-Cookie": cookies }));
  res.end(JSON.stringify(data));
}

/** 보낼 쿠키가 없을 때 빈 Set-Cookie 헤더를 만들지 않습니다. */
function withoutEmpty(headers: Record<string, string | string[]>) {
  return Object.fromEntries(Object.entries(headers).filter(([, v]) => !(Array.isArray(v) && v.length === 0)));
}

function parseCookies(header: string | undefined): Record<string, string> {
  const cookies: Record<string, string> = {};
  for (const part of (header ?? "").split(";")) {
    const index = part.indexOf("=");
    if (index > 0) cookies[part.slice(0, index).trim()] = decodeURIComponent(part.slice(index + 1).trim());
  }
  return cookies;
}

async function readBody(req: IncomingMessage) {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  if (chunks.length === 0) return undefined;
  const text = Buffer.concat(chunks).toString("utf8");
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    throw new HttpError(400, "INVALID_INPUT", "요청 형식을 확인해 주세요.");
  }
}

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export const notFound = (what: string): never => {
  throw new HttpError(404, "NOT_FOUND", `${what}을(를) 찾을 수 없어요.`);
};
