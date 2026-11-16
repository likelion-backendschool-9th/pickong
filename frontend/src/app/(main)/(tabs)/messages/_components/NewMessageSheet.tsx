"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useConversations, useStartConversation } from "@/entities/conversation";
import { useFollowing, useMe, useUserSearch } from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { useDebounced } from "@/shared/lib/useDebounced";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Avatar } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { useToast } from "@/shared/ui/Toast";

/**
 * 새 메시지: 받을 사람을 고르면 대화방을 열거나 만들고 바로 이동합니다.
 * 검색어가 없으면 내가 팔로우하는 사람을 먼저 보여 줍니다.
 */
export function NewMessageSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="새 메시지" className="h-[85dvh]">
      {open && <Picker onDone={onClose} />}
    </BottomSheet>
  );
}

function Picker({ onDone }: { onDone: () => void }) {
  const [query, setQuery] = useState("");
  const debounced = useDebounced(query.trim());
  const { data: me } = useMe();
  const { data: following = [] } = useFollowing(me?.username ?? "");
  const { data: found = [], isFetching } = useUserSearch(debounced);
  const { data: conversations = [] } = useConversations();
  const start = useStartConversation();
  const router = useRouter();
  const toast = useToast();

  const people = (query.trim() ? found : following).filter((u) => !u.isMe);
  const talkingWith = new Map(conversations.map((c) => [c.partner.id, c.id]));

  const open = (username: string, id: string) => {
    const existing = talkingWith.get(id);
    if (existing) {
      onDone();
      return router.push(routes.conversation(existing));
    }
    start.mutate(username, {
      onSuccess: (c) => {
        onDone();
        router.push(routes.conversation(c.id));
      },
      onError: (e) => toast({ message: errorMessage(e) }),
    });
  };

  return (
    <div>
      <SearchInput autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder="받을 사람 검색" aria-label="받을 사람 검색" />
      <h3 className="mt-4 mb-1 text-[13px] font-bold">{query.trim() ? "검색 결과" : "팔로잉"}</h3>
      {people.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-ink-sub">
          {query.trim()
            ? isFetching || debounced !== query.trim()
              ? "찾는 중…"
              : `‘${query.trim()}’와 일치하는 사람이 없어요`
            : "이름으로 받을 사람을 찾아 보세요."}
        </p>
      ) : (
        <ul>
          {people.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                disabled={start.isPending}
                onClick={() => open(u.username, u.id)}
                className="flex w-full items-center gap-3 py-2.5 text-left active:bg-fill disabled:opacity-60"
              >
                <Avatar src={u.profileImageUrl} name={u.nickname} size={40} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[14px] font-bold">{u.nickname}</span>
                  <span className="block truncate text-[12px] text-ink-sub">@{u.username}</span>
                </span>
                {talkingWith.has(u.id) && <span className="shrink-0 text-[12px] text-ink-faint">대화 중</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
