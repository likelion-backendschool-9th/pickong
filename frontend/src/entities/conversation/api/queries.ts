import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Message, OutgoingAttachment } from "../model";
import { conversationApi } from "./conversationApi";

/** 새 메시지를 확인하는 주기 (실시간 연결 전까지 주기적으로 다시 불러옵니다) */
const POLL_MS = 5_000;

export const conversationKeys = {
  all: ["conversations"] as const,
  list: () => [...conversationKeys.all, "list"] as const,
  unread: () => [...conversationKeys.all, "unread"] as const,
  detail: (id: string) => [...conversationKeys.all, "detail", id] as const,
  messages: (id: string) => [...conversationKeys.all, "messages", id] as const,
};

export const useConversations = () =>
  useQuery({ queryKey: conversationKeys.list(), queryFn: conversationApi.list, refetchInterval: POLL_MS * 6 });

/**
 * 안 읽은 메시지 수 (하단바 배지).
 * 대화 목록과 다른 키로 따로 불러옵니다. 같은 데이터를 쓰면 메시지 화면이 서버 렌더(로딩 중)와
 * 하단바가 먼저 받아 둔 목록으로 다르게 그려져 hydration 불일치가 납니다.
 */
export const useUnreadMessageCount = () =>
  useQuery({ queryKey: conversationKeys.unread(), queryFn: conversationApi.unreadCount, refetchInterval: POLL_MS * 6 }).data ?? 0;

export const useConversation = (id: string) =>
  useQuery({ queryKey: conversationKeys.detail(id), queryFn: () => conversationApi.get(id) });

/** 대화방 메시지. 열려 있는 동안 주기적으로 새 메시지를 확인하고, 읽음 처리 후 목록 배지를 갱신합니다. */
export function useMessages(id: string) {
  const qc = useQueryClient();
  return useQuery({
    queryKey: conversationKeys.messages(id),
    queryFn: async () => {
      const list = await conversationApi.messages(id);
      // 읽음 처리됐으므로 대화 목록 · 배지 갱신 (메시지 자신은 다시 부르지 않도록 키를 골라서)
      qc.invalidateQueries({ queryKey: conversationKeys.list() });
      qc.invalidateQueries({ queryKey: conversationKeys.unread() });
      return list;
    },
    refetchInterval: POLL_MS,
  });
}

export function useSendMessage(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { text: string; attachment?: OutgoingAttachment | null }) => conversationApi.send(id, input),
    onSuccess: (message) => {
      qc.setQueryData<Message[]>(conversationKeys.messages(id), (prev) => [...(prev ?? []), message]);
      qc.invalidateQueries({ queryKey: conversationKeys.list() });
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
