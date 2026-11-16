import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Message, OutgoingAttachment } from "../model";
import { conversationApi } from "./conversationApi";

/** 새 메시지를 확인하는 주기 (실시간 연결 전까지 주기적으로 다시 불러옵니다) */
const POLL_MS = 5_000;

export const conversationKeys = {
  all: ["conversations"] as const,
  list: () => [...conversationKeys.all, "list"] as const,
  detail: (id: string) => [...conversationKeys.all, "detail", id] as const,
  messages: (id: string) => [...conversationKeys.all, "messages", id] as const,
};

export const useConversations = () =>
  useQuery({ queryKey: conversationKeys.list(), queryFn: conversationApi.list, refetchInterval: POLL_MS * 6 });

/** 안 읽은 메시지 수 (하단바 배지) */
export const useUnreadMessageCount = () => {
  const { data } = useConversations();
  return data?.reduce((sum, c) => sum + c.unreadCount, 0) ?? 0;
};

export const useConversation = (id: string) =>
  useQuery({ queryKey: conversationKeys.detail(id), queryFn: () => conversationApi.get(id) });

/** 대화방 메시지. 열려 있는 동안 주기적으로 새 메시지를 확인하고, 읽음 처리 후 목록 배지를 갱신합니다. */
export function useMessages(id: string) {
  const qc = useQueryClient();
  return useQuery({
    queryKey: conversationKeys.messages(id),
    queryFn: async () => {
      const list = await conversationApi.messages(id);
      qc.invalidateQueries({ queryKey: conversationKeys.list() });
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
