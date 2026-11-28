/**
 * mock STOMP 서버: ws://localhost:4000/api/v1/ws (common_blueprint 5.9).
 * 프론트가 mock과 백엔드에서 같은 코드로 채팅하도록, 의존성 없이 node 내장 모듈로 WebSocket과 STOMP의 필요한 부분만 구현합니다.
 * - WebSocket: 핸드셰이크, 텍스트 · ping · close 프레임 (조각난 프레임은 다루지 않음)
 * - STOMP 1.2: CONNECT · SUBSCRIBE · UNSUBSCRIBE · SEND · DISCONNECT. heartbeat는 쓰지 않음(0,0)
 * - 모든 연결은 mock의 내 계정이고, 이벤트는 열린 연결(탭) 모두에 보냅니다.
 */
import { createHash } from "node:crypto";
import type { IncomingMessage, Server } from "node:http";
import type { Duplex } from "node:stream";
import { markRead, messageDto, sendMessage, systemListeners } from "./handlers/messages.ts";
import { ME_ID } from "./db.ts";
import { HttpError } from "./router.ts";

const PATH = "/api/v1/ws";
const WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const DM = "/user/queue/dm";
const ERRORS = "/user/queue/errors";

const sessions = new Set<StompSession>();

// HTTP로 만든 그룹 안내 메시지(만들기 · 초대 · 이름 변경)도 열린 연결에 보냅니다 (백엔드와 같음)
systemListeners.push((message) => {
  const event = { type: "MESSAGE_CREATED", data: { ...messageDto(message), clientMessageId: null } };
  sessions.forEach((s) => s.push(DM, event));
});

export function attachStomp(server: Server) {
  server.on("upgrade", (req: IncomingMessage, socket: Duplex) => {
    const key = req.headers["sec-websocket-key"];
    if (new URL(req.url ?? "/", "http://localhost").pathname !== PATH || typeof key !== "string") {
      socket.end("HTTP/1.1 404 Not Found\r\n\r\n");
      return;
    }
    const accept = createHash("sha1").update(key + WS_GUID).digest("base64");
    const offered = String(req.headers["sec-websocket-protocol"] ?? "").split(",").map((p) => p.trim());
    const protocol = offered.includes("v12.stomp") ? "v12.stomp" : undefined;
    socket.write(
      [
        "HTTP/1.1 101 Switching Protocols",
        "Upgrade: websocket",
        "Connection: Upgrade",
        `Sec-WebSocket-Accept: ${accept}`,
        ...(protocol ? [`Sec-WebSocket-Protocol: ${protocol}`] : []),
        "",
        "",
      ].join("\r\n"),
    );
    sessions.add(new StompSession(socket));
  });
}

class StompSession {
  private buffer = Buffer.alloc(0);
  private text = "";
  /** 목적지 → 구독 id */
  private readonly subscriptions = new Map<string, string>();
  private messageSeq = 0;
  private readonly socket: Duplex;

  // node의 TypeScript 실행(타입만 지움)은 생성자 매개변수 속성을 지원하지 않아 필드에 직접 넣습니다.
  constructor(socket: Duplex) {
    this.socket = socket;
    socket.on("data", (chunk: Buffer) => this.onData(chunk));
    socket.on("close", () => sessions.delete(this));
    socket.on("error", () => sessions.delete(this));
  }

  // ---- WebSocket 프레임 ----

  private onData(chunk: Buffer) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    for (;;) {
      const frame = readFrame(this.buffer);
      if (!frame) return;
      this.buffer = this.buffer.subarray(frame.size);
      if (frame.opcode === 0x1) this.onText(frame.payload.toString("utf8"));
      else if (frame.opcode === 0x9) this.writeFrame(0xa, frame.payload);
      else if (frame.opcode === 0x8) return this.close();
    }
  }

  private writeFrame(opcode: number, payload: Buffer) {
    const length = payload.length;
    const header =
      length < 126
        ? Buffer.from([0x80 | opcode, length])
        : length < 65_536
          ? Buffer.from([0x80 | opcode, 126, length >> 8, length & 0xff])
          : Buffer.concat([Buffer.from([0x80 | opcode, 127]), bigEndian64(length)]);
    if (!this.socket.destroyed) this.socket.write(Buffer.concat([header, payload]));
  }

  private close() {
    this.writeFrame(0x8, Buffer.alloc(0));
    this.socket.end();
    sessions.delete(this);
  }

  // ---- STOMP 프레임 ----

  private onText(data: string) {
    this.text += data;
    let end: number;
    while ((end = this.text.indexOf("\0")) >= 0) {
      const raw = this.text.slice(0, end).replace(/^[\r\n]+/, "");
      this.text = this.text.slice(end + 1);
      if (raw) this.onStomp(parseStomp(raw));
    }
  }

  private sendStomp(command: string, headers: Record<string, string>, body = "") {
    const head = Object.entries(headers).map(([k, v]) => `${k}:${v}`);
    this.writeFrame(0x1, Buffer.from([command, ...head, "", body].join("\n") + "\0", "utf8"));
  }

  /** 이 연결이 구독 중인 목적지로 JSON을 보냅니다. */
  push(destination: string, body: unknown) {
    const subscription = this.subscriptions.get(destination);
    if (!subscription) return;
    this.sendStomp(
      "MESSAGE",
      { destination, subscription, "message-id": String(++this.messageSeq), "content-type": "application/json" },
      JSON.stringify(body),
    );
  }

  /** 연결 단위 거절: ERROR(message = 코드) 후 연결 종료 (백엔드와 같음) */
  private reject(code: string) {
    this.sendStomp("ERROR", { message: code });
    this.close();
  }

  private onStomp({ command, headers, body }: StompFrame) {
    switch (command) {
      case "CONNECT":
      case "STOMP":
        return this.sendStomp("CONNECTED", { version: "1.2", "heart-beat": "0,0" });
      case "SUBSCRIBE":
        if (!headers.destination?.startsWith("/user/queue/")) return this.reject("FORBIDDEN_DESTINATION");
        this.subscriptions.set(headers.destination, headers.id ?? "0");
        return;
      case "UNSUBSCRIBE":
        for (const [destination, id] of this.subscriptions) if (id === headers.id) this.subscriptions.delete(destination);
        return;
      case "SEND":
        if (!headers.destination?.startsWith("/app/")) return this.reject("FORBIDDEN_DESTINATION");
        return this.onSend(headers.destination, body);
      case "DISCONNECT":
        if (headers.receipt) this.sendStomp("RECEIPT", { "receipt-id": headers.receipt });
        return this.close();
    }
  }

  private onSend(destination: string, rawBody: string) {
    let body: Record<string, unknown> = {};
    try {
      body = JSON.parse(rawBody || "{}");
    } catch {
      return this.push(ERRORS, errorBody(new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요."), null));
    }
    const clientMessageId = typeof body.clientMessageId === "string" ? body.clientMessageId : null;
    try {
      if (destination === "/app/dm.send") {
        const { message, created } = sendMessage(body);
        const event = { type: "MESSAGE_CREATED", data: { ...messageDto(message), clientMessageId } };
        // 새 메시지는 내 모든 탭에, 다시 보낸 것은 보낸 연결에만 (백엔드와 같음)
        if (created) sessions.forEach((s) => s.push(DM, event));
        else this.push(DM, event);
      } else if (destination === "/app/dm.read") {
        const conversationId = String(body.conversationId ?? "");
        const lastReadMessageId = markRead(conversationId, body.lastReadMessageId);
        if (lastReadMessageId) {
          const event = { type: "READ_UPDATED", data: { conversationId, userId: ME_ID, lastReadMessageId } };
          sessions.forEach((s) => s.push(DM, event));
        }
      }
    } catch (error) {
      const httpError = error instanceof HttpError ? error : new HttpError(500, "INTERNAL_SERVER_ERROR", "mock 서버 오류");
      this.push(ERRORS, errorBody(httpError, destination === "/app/dm.send" ? clientMessageId : null));
    }
  }
}

const errorBody = (error: HttpError, clientMessageId: string | null) => ({
  status: error.status,
  code: error.code,
  message: error.message,
  clientMessageId,
});

interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body: string;
}

function parseStomp(raw: string): StompFrame {
  const blank = raw.indexOf("\n\n");
  const head = (blank >= 0 ? raw.slice(0, blank) : raw).split("\n").map((l) => l.replace(/\r$/, ""));
  const headers: Record<string, string> = {};
  for (const line of head.slice(1)) {
    const colon = line.indexOf(":");
    if (colon > 0 && !(line.slice(0, colon) in headers)) headers[line.slice(0, colon)] = line.slice(colon + 1);
  }
  return { command: head[0], headers, body: blank >= 0 ? raw.slice(blank + 2) : "" };
}

/** 클라이언트가 보낸 WebSocket 프레임 하나. 아직 다 오지 않았으면 null */
function readFrame(buffer: Buffer): { opcode: number; payload: Buffer; size: number } | null {
  if (buffer.length < 2) return null;
  const opcode = buffer[0] & 0x0f;
  const masked = (buffer[1] & 0x80) !== 0;
  let length = buffer[1] & 0x7f;
  let offset = 2;
  if (length === 126) {
    if (buffer.length < 4) return null;
    length = buffer.readUInt16BE(2);
    offset = 4;
  } else if (length === 127) {
    if (buffer.length < 10) return null;
    length = Number(buffer.readBigUInt64BE(2));
    offset = 10;
  }
  const maskOffset = offset;
  if (masked) offset += 4;
  if (buffer.length < offset + length) return null;
  const payload = Buffer.from(buffer.subarray(offset, offset + length));
  if (masked) for (let i = 0; i < payload.length; i += 1) payload[i] ^= buffer[maskOffset + (i % 4)];
  return { opcode, payload, size: offset + length };
}

function bigEndian64(value: number) {
  const out = Buffer.alloc(8);
  out.writeBigUInt64BE(BigInt(value));
  return out;
}
