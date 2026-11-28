export const conversationKeys = {
  all: ["conversations"] as const,
  list: () => [...conversationKeys.all, "list"] as const,
  unread: () => [...conversationKeys.all, "unread"] as const,
  detail: (id: string) => [...conversationKeys.all, "detail", id] as const,
  /** 대화방 메시지 (MessageThread). 실시간 이벤트를 이 캐시에 직접 넣습니다 */
  messages: (id: string) => [...conversationKeys.all, "messages", id] as const,
};
