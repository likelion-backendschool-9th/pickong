import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef } from "react";
import { latestServerMessage, mergeMessages } from "../messageList";
import type { Conversation, Message, MessageAttachment, MessageThread, OutgoingAttachment } from "../model";
import { conversationApi } from "./conversationApi";
import { conversationKeys } from "./keys";
import { sendMessage, type SendInput } from "./realtime";

export { conversationKeys };

/*
 * 새 메시지 · 읽음은 실시간 이벤트(./realtime)가 캐시를 갱신하므로 주기적으로 다시 불러오지 않습니다.
 * 끊긴 동안 놓친 것은 다시 연결 · 탭 복귀 때 보충합니다.
 */

export const useConversations = () => useQuery({ queryKey: conversationKeys.list(), queryFn: conversationApi.list });

/**
 * 안 읽은 메시지 수 (하단바 배지).
 * 대화 목록과 다른 키로 따로 불러옵니다. 같은 데이터를 쓰면 메시지 화면이 서버 렌더(로딩 중)와
 * 하단바가 먼저 받아 둔 목록으로 다르게 그려져 hydration 불일치가 납니다.
 */
export const useUnreadMessageCount = () =>
  useQuery({ queryKey: conversationKeys.unread(), queryFn: conversationApi.unreadCount }).data ?? 0;

export const useConversation = (id: string) =>
  useQuery({ queryKey: conversationKeys.detail(id), queryFn: () => conversationApi.get(id) });

/** 대화방 메시지 (오래된 순). 처음에는 최신 한 페이지만 불러오고, 이전 내역은 useLoadOlderMessages로 앞에 붙입니다. */
export const useMessages = (id: string) =>
  useQuery({
    queryKey: conversationKeys.messages(id),
    queryFn: async (): Promise<MessageThread> => {
      const page = await conversationApi.messages(id);
      return { items: page.items, olderCursor: page.nextCursor };
    },
    // 실시간으로 받은 메시지와 보내는 중 말풍선이 캐시에만 있으므로, 화면을 다시 열 때 덮어쓰지 않게 합니다.
    staleTime: Infinity,
  });

export function useLoadOlderMessages(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const cursor = qc.getQueryData<MessageThread>(conversationKeys.messages(id))?.olderCursor;
      return cursor ? conversationApi.messages(id, cursor) : null;
    },
    onSuccess: (page) => {
      if (!page) return;
      qc.setQueryData<MessageThread>(conversationKeys.messages(id), (thread) =>
        thread ? { items: mergeMessages(thread.items, page.items), olderCursor: page.nextCursor } : thread,
      );
    },
  });
}

/**
 * 대화방을 보고 있는 동안, 상대가 보낸 마지막 메시지까지 읽음으로 표시합니다(탭이 보일 때만).
 * 조회만으로는 읽음이 되지 않으므로(backend 3장) 따로 보냅니다.
 */
export function useMarkReadWhileVisible(id: string, items: Message[] | undefined) {
  const qc = useQueryClient();
  const marked = useRef<string | null>(null);
  const last = items ? latestServerMessage(items) : null;
  const lastId = last && !last.isMine ? last.id : null;

  const mark = useCallback(() => {
    if (!lastId || marked.current === lastId || document.visibilityState !== "visible") return;
    marked.current = lastId;
    conversationApi
      .markRead(id, lastId)
      .then(() => {
        qc.invalidateQueries({ queryKey: conversationKeys.unread() });
        qc.invalidateQueries({ queryKey: conversationKeys.list() });
        qc.invalidateQueries({ queryKey: conversationKeys.detail(id) });
      })
      .catch(() => {
        marked.current = null;
      });
  }, [id, lastId, qc]);

  useEffect(() => {
    mark();
    document.addEventListener("visibilitychange", mark);
    return () => document.removeEventListener("visibilitychange", mark);
  }, [mark]);
}

/** 메시지 보내기 (STOMP). 실패하면 말풍선이 '실패'로 남고 retry로 같은 clientMessageId로 다시 보냅니다. */
export function useSendMessage(id: string) {
  const qc = useQueryClient();
  return {
    send: (input: SendInput) => sendMessage(qc, id, input),
    retry: (message: Message) =>
      sendMessage(
        qc,
        id,
        { text: message.text, attachment: toOutgoing(message.attachment), preview: message.attachment },
        message.clientMessageId,
      ),
  };
}

/** 다시 보낼 때 말풍선의 첨부를 보낼 형식으로 되돌립니다. 게시물 공유는 입력창에서 만들지 않으므로 없음 */
function toOutgoing(attachment: MessageAttachment | null): OutgoingAttachment | null {
  if (attachment?.type === "IMAGE") return { type: "IMAGE", url: attachment.url };
  if (attachment?.type === "PLACE" && attachment.place) return { type: "PLACE", placeId: attachment.place.id };
  return null;
}

/** 대화방 정보를 바꾸는 요청: 응답(최신 대화방)으로 상세 캐시를 바로 바꾸고 목록을 다시 불러옵니다. */
function useConversationMutation<T>(fn: (input: T) => Promise<Conversation>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (c) => {
      qc.setQueryData(conversationKeys.detail(c.id), c);
      qc.invalidateQueries({ queryKey: conversationKeys.list() });
    },
  });
}

/** 그룹 만들기 (나 포함 3~10명) */
export const useCreateGroup = () => useConversationMutation(conversationApi.createGroup);

/** 그룹 이름(멤버 누구나) · 내 알림 끄기 */
export const useUpdateConversation = (id: string) =>
  useConversationMutation((input: { title?: string; muted?: boolean }) => conversationApi.update(id, input));

/** 그룹 초대(멤버 누구나, 아이디로) */
export const useInviteMembers = (id: string) => useConversationMutation((usernames: string[]) => conversationApi.invite(id, usernames));

/** 나가기: 이 대화방의 캐시를 지우고 목록 · 안 읽은 수를 다시 불러옵니다. */
export function useLeaveConversation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => conversationApi.leave(id),
    onSuccess: () => {
      qc.removeQueries({ queryKey: conversationKeys.detail(id) });
      qc.removeQueries({ queryKey: conversationKeys.messages(id) });
      qc.invalidateQueries({ queryKey: conversationKeys.list() });
      qc.invalidateQueries({ queryKey: conversationKeys.unread() });
    },
  });
}

export function useStartConversation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: conversationApi.start,
    onSuccess: (c) => qc.setQueryData(conversationKeys.detail(c.id), c),
  });
}
