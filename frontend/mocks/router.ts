import type { IncomingMessage, ServerResponse } from "node:http";

export class HttpError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export interface Ctx {
  params: Record<string, string>;
  query: URLSearchParams;
  body: any;
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
      const data = await route.handler({ params, query: url.searchParams, body });
      if (data === undefined) {
        res.writeHead(204).end();
      } else {
        send(res, 200, data);
      }
    } catch (error) {
      if (error instanceof HttpError) {
        send(res, error.status, { code: error.code, message: error.message });
      } else {
        console.error(error);
        send(res, 500, { code: "INTERNAL", message: "mock 서버 오류" });
      }
    }
  };
}

function send(res: ServerResponse, status: number, data: unknown) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(data));
}

async function readBody(req: IncomingMessage) {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  if (chunks.length === 0) return undefined;
  const text = Buffer.concat(chunks).toString("utf8");
  return text ? JSON.parse(text) : undefined;
}

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export const notFound = (what: string): never => {
  throw new HttpError(404, "NOT_FOUND", `${what}을(를) 찾을 수 없어요.`);
};
