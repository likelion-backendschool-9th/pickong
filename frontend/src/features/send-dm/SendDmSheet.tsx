"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useState } from "react";
import {
  conversationApi,
  conversationKeys,
  matchesConversation,
  sendMessage,
  useConversationRealtime,
  useConversations,
} from "@/entities/conversation";
import { useUserSearch } from "@/entities/user";
import { useDebounced } from "@/shared/lib/useDebounced";
import { errorMessage } from "@/shared/api/errors";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Avatar, Spinner } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { useToast } from "@/shared/ui/Toast";

interface Target {
  username: string;
  profileImageUrl: string | null;
  /** 이미 대화 중이면 대화방 id */
  conversationId?: string;
}

/**
 * DM으로 보내기: 받을 사람을 고르면 대화방을 열거나 만들어 text를 메시지로 보냅니다.
 * 최근 대화한 사람이 먼저 보이고, 검색하면 아직 대화하지 않은 사람도 찾을 수 있습니다.
 */
export function SendDmSheet({ open, onClose, text, title = "DM으로 보내기" }: { open: boolean; onClose: () => void; text: string; title?: string }) {
  return (
    <BottomSheet open={open} onClose={onClose} title={title} className="h-[85dvh]">
      {open && <Picker text={text} onDone={onClose} />}
    </BottomSheet>
  );
}

function Picker({ text, onDone }: { text: string; onDone: () => void }) {
  // 게시물 · 장소 화면처럼 하단바가 없는 곳에서도 열리므로, 보내기 확인을 받을 연결을 여기서도 켭니다.
  useConversationRealtime();
  const [query, setQuery] = useState("");
  const debounced = useDebounced(query.trim());
  const { data: conversations = [] } = useConversations();
  const { data: users, isFetching } = useUserSearch(debounced);
  const qc = useQueryClient();
  const toast = useToast();

  const send = useMutation({
    mutationFn: async (target: Target) => {
      const id = target.conversationId ?? (await conversationApi.start(target.username)).id;
      await sendMessage(qc, id, { text });
    },
    onSuccess: (_r, target) => {
      qc.invalidateQueries({ queryKey: conversationKeys.all });
      toast({ message: `${target.username}님에게 보냈어요` });
      onDone();
    },
    onError: (e) => toast({ message: errorMessage(e) }),
  });

  const talking = new Set(conversations.map((c) => c.partner.id));
  const targets: Target[] = [
    ...conversations
      .filter((c) => matchesConversation(c, query))
      .map((c) => ({ ...c.partner, conversationId: c.id })),
    ...(query.trim() ? (users ?? []).filter((u) => !u.isMe && !talking.has(u.id)) : []),
  ];

  return (
    <div>
      <div className="relative">
        <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
        <Input
          type="search"
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="받을 사람 검색"
          aria-label="받을 사람 검색"
          className="pl-10"
        />
        {isFetching && <Spinner className="absolute top-1/2 right-3.5 size-4 -translate-y-1/2" />}
      </div>

      <p className="mt-3 rounded-card bg-fill px-3 py-2.5 text-[12px] whitespace-pre-line text-ink-sub">{text}</p>

      <h3 className="mt-4 mb-1 text-[13px] font-bold">{query.trim() ? "검색 결과" : "최근 대화"}</h3>
      {targets.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-ink-sub">
          {query.trim() ? (isFetching || debounced !== query.trim() ? "찾는 중…" : `‘${query.trim()}’와 일치하는 사람이 없어요`) : "이름으로 받을 사람을 찾아 보세요."}
        </p>
      ) : (
        <ul>
          {targets.map((t) => (
            <li key={t.username} className="flex items-center gap-3 py-2">
              <Avatar src={t.profileImageUrl} name={t.username} size={40} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[14px] font-bold">{t.username}</span>
              </span>
              <button
                type="button"
                disabled={send.isPending}
                onClick={() => send.mutate(t)}
                className="h-8 shrink-0 rounded-full bg-primary px-3.5 text-[13px] font-bold text-on-primary disabled:bg-fill disabled:text-ink-faint"
              >
                {send.isPending && send.variables?.username === t.username ? "보내는 중" : "보내기"}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
