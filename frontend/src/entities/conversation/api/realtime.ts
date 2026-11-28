"use client";

import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { ApiError } from "@/shared/api/errors";
import { realtime } from "@/shared/realtime/connection";
import { latestServerMessage, markFailed, markSending, mergeMessages, pendingId, upsertMessage } from "../messageList";
import type { Message, MessageAttachment, MessageThread, OutgoingAttachment } from "../model";
import { conversationApi, type MessagePage } from "./conversationApi";
import type { DmEventDto, StompErrorDto } from "./dto";
import { toMessage } from "./mapper";
import { conversationKeys } from "./keys";

/**
 * 대화 실시간 (common_blueprint 5.9, frontend_blueprint 8.8).
 * - /user/queue/dm 이벤트를 TanStack Query 캐시에 반영합니다. 컴포넌트는 이벤트를 직접 구독하지 않습니다.
 * - (재)연결 · 탭 복귀 때 HTTP로 다시 맞춥니다(목록 · 안 읽은 수 · 열어 둔 대화방은 after로 보충).
 * - 보내기는 STOMP /app/dm.send. 보내는 중 말풍선을 먼저 넣고, 서버 확인(MESSAGE_CREATED)이 오면 바꿉니다.
 */

const DM = "/user/queue/dm";
const ERRORS = "/user/queue/errors";
const SEND = "/app/dm.send";
/** 이 시간 안에 서버 확인이 없으면 실패로 표시 (다시 보낼 때는 같은 clientMessageId) */
const CONFIRM_TIMEOUT_MS = 10_000;
const CONNECT_TIMEOUT_MS = 5_000;

const NOT_CONNECTED = () => new ApiError(0, "REALTIME_UNAVAILABLE", "연결이 끊겼어요. 잠시 후 다시 보내 주세요.");
const TIMED_OUT = () => new ApiError(0, "MESSAGE_TIMEOUT", "메시지를 보내지 못했어요. 다시 보내 주세요.");

interface Pending {
  conversationId: string;
  resolve: (message: Message) => void;
  reject: (error: ApiError) => void;
}

/** 서버 확인을 기다리는 메시지 (clientMessageId → 기다리는 쪽) */
const pending = new Map<string, Pending>();

const updateThread = (qc: QueryClient, conversationId: string, update: (items: Message[]) => Message[]) =>
  qc.setQueryData<MessageThread>(conversationKeys.messages(conversationId), (thread) =>
    thread ? { ...thread, items: update(thread.items) } : thread,
  );

/** 목록 미리보기 · 안 읽은 수 · 대화방 상세를 다시 불러오게 합니다 (메시지 내역은 캐시에 직접 넣으므로 제외). */
const refreshSummaries = (qc: QueryClient, conversationId?: string) => {
  qc.invalidateQueries({ queryKey: conversationKeys.list() });
  qc.invalidateQueries({ queryKey: conversationKeys.unread() });
  qc.invalidateQueries({ queryKey: conversationId ? conversationKeys.detail(conversationId) : [...conversationKeys.all, "detail"] });
};

function handleEvent(qc: QueryClient, event: DmEventDto) {
  if (event.type === "MESSAGE_CREATED") {
    const message = toMessage((event as Extract<DmEventDto, { type: "MESSAGE_CREATED" }>).data);
    updateThread(qc, message.conversationId, (items) => upsertMessage(items, message));
    if (message.isMine && message.clientMessageId) pending.get(message.clientMessageId)?.resolve(message);
    refreshSummaries(qc, message.conversationId);
  } else if (event.type === "READ_UPDATED") {
    const { conversationId } = (event as Extract<DmEventDto, { type: "READ_UPDATED" }>).data;
    refreshSummaries(qc, conversationId);
  }
}

function handleError(error: StompErrorDto) {
  if (!error.clientMessageId) return;
  pending.get(error.clientMessageId)?.reject(new ApiError(error.status, error.code, error.message));
}

/** 끊긴 동안의 이벤트는 다시 오지 않으므로 HTTP로 보충합니다. */
async function resync(qc: QueryClient) {
  refreshSummaries(qc);
  const threads = qc.getQueryCache().findAll({ queryKey: [...conversationKeys.all, "messages"] });
  await Promise.all(
    threads.map(async (query) => {
      const conversationId = query.queryKey[2] as string;
      const last = latestServerMessage((query.state.data as MessageThread | undefined)?.items ?? []);
      if (!last) return qc.invalidateQueries({ queryKey: query.queryKey });
      let after: string | null = last.id;
      while (after) {
        const page: MessagePage | null = await conversationApi.messagesAfter(conversationId, after).catch(() => null);
        if (!page) return;
        updateThread(qc, conversationId, (items) => mergeMessages(items, page.items));
        after = page.nextCursor;
      }
    }),
  );
}

let installs = 0;
let uninstall: (() => void) | null = null;

function install(qc: QueryClient) {
  installs += 1;
  if (installs > 1) return;
  const release = realtime.acquire();
  // 연결 실패 뒤에는 핸드셰이크와 같은 보안 필터를 거치는 API로 상태를 확인합니다. 401은 재발급, 403은 재동의 화면으로 (공통 처리)
  realtime.setProbe(() =>
    qc.fetchQuery({ queryKey: conversationKeys.unread(), queryFn: conversationApi.unreadCount, staleTime: 0 }),
  );
  const offDm = realtime.subscribe(DM, (body) => handleEvent(qc, body as DmEventDto));
  const offErrors = realtime.subscribe(ERRORS, (body) => handleError(body as StompErrorDto));
  const offConnected = realtime.onConnected(() => void resync(qc));
  const onVisible = () => {
    if (document.visibilityState === "visible") void resync(qc);
  };
  document.addEventListener("visibilitychange", onVisible);
  uninstall = () => {
    document.removeEventListener("visibilitychange", onVisible);
    offConnected();
    offErrors();
    offDm();
    realtime.setProbe(null);
    release();
  };
}

function uninstallOnce() {
  installs -= 1;
  if (installs > 0) return;
  uninstall?.();
  uninstall = null;
}

/** 대화 실시간을 켭니다. 하단바 · 대화방처럼 대화를 보여 주는 곳에서 부릅니다(여러 곳에서 불러도 연결 · 구독은 하나). */
export function useConversationRealtime() {
  const qc = useQueryClient();
  useEffect(() => {
    install(qc);
    return uninstallOnce;
  }, [qc]);
}

export interface SendInput {
  text: string;
  /** 서버로 보내는 첨부 (지금 백엔드는 받지 않아 ATTACHMENT_NOT_SUPPORTED로 거절) */
  attachment?: OutgoingAttachment | null;
  /** 보내는 중 말풍선에 보여 줄 첨부 */
  preview?: MessageAttachment | null;
}

/**
 * 메시지를 보냅니다. 보내는 중 말풍선을 먼저 넣고, 서버 확인이 오면 저장된 메시지를 돌려줍니다.
 * 실패(서버 오류 · 시간 초과 · 연결 없음)하면 말풍선을 '실패'로 바꾸고 ApiError로 거절합니다.
 * 다시 보낼 때는 같은 clientMessageId를 넘깁니다(서버가 두 번 저장하지 않음).
 */
export function sendMessage(
  qc: QueryClient,
  conversationId: string,
  input: SendInput,
  clientMessageId: string = crypto.randomUUID(),
): Promise<Message> {
  const optimistic: Message = {
    id: pendingId(clientMessageId),
    conversationId,
    senderId: "",
    isMine: true,
    text: input.text,
    attachment: input.preview ?? null,
    createdAt: new Date().toISOString(),
    clientMessageId,
    status: "sending",
  };
  updateThread(qc, conversationId, (items) =>
    items.some((m) => m.id === optimistic.id) ? markSending(items, clientMessageId) : upsertMessage(items, optimistic),
  );

  const release = realtime.acquire();
  return new Promise<Message>((resolve, reject) => {
    const settle = () => {
      clearTimeout(timer);
      pending.delete(clientMessageId);
      release();
    };
    const fail = (error: ApiError) => {
      settle();
      updateThread(qc, conversationId, (items) => markFailed(items, clientMessageId));
      reject(error);
    };
    const timer = setTimeout(() => fail(TIMED_OUT()), CONFIRM_TIMEOUT_MS);
    pending.set(clientMessageId, {
      conversationId,
      resolve: (message) => {
        settle();
        resolve(message);
      },
      reject: fail,
    });
    realtime
      .whenConnected(CONNECT_TIMEOUT_MS)
      .then(() => {
        const sent = realtime.publish(SEND, {
          conversationId,
          clientMessageId,
          text: input.text,
          attachment: input.attachment ?? null,
        });
        if (!sent) fail(NOT_CONNECTED());
      })
      .catch(() => fail(NOT_CONNECTED()));
  });
}
