"use client";

import { Search } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  matchesConversation,
  messagePreview,
  useConversations,
  useStartConversation,
  type Conversation,
} from "@/entities/conversation";
import { useUserSearch, type User } from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatRelative } from "@/shared/lib/date";
import { Avatar, EmptyState, ErrorState, SectionTitle, Skeleton, Spinner } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { useToast } from "@/shared/ui/Toast";

/** 사용자 검색 요청은 입력을 멈추고 잠시 뒤에 보냅니다. */
const SEARCH_DELAY = 250;

/**
 * DM 대화 목록: 최근 메시지 순, 안 읽은 대화는 굵게 + 개수 배지.
 * 검색하면 대화(상대 이름 · 마지막 메시지)를 거르고, 아직 대화하지 않은 사람도 찾아서 바로 메시지를 보낼 수 있습니다.
 */
export function MessagesScreen() {
  const { data, isPending, isError, error, refetch } = useConversations();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const searching = query.trim().length > 0;

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), SEARCH_DELAY);
    return () => clearTimeout(t);
  }, [query]);

  return (
    <div>
      <header className="sticky top-0 z-30 bg-bg">
        <div className="flex h-[52px] items-center px-5">
          <h1 className="text-[20px] font-bold">메시지</h1>
        </div>
        <div className="px-4 pb-3">
          <div className="relative">
            <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
            <Input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="이름 · 아이디 · 메시지 검색"
              aria-label="대화와 사람 검색"
              className="h-11 pl-10"
            />
          </div>
        </div>
      </header>

      {isPending ? (
        <div className="space-y-3 px-5 pt-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="size-12 rounded-full" />
              <Skeleton className="h-4 flex-1" />
            </div>
          ))}
        </div>
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : searching ? (
        <SearchResults query={query.trim()} debounced={debounced} conversations={data} />
      ) : data.length === 0 ? (
        <EmptyState title="아직 대화가 없어요" description="위에서 사람을 검색하거나, 친구 프로필의 ‘메시지’ 버튼으로 대화를 시작해 보세요." />
      ) : (
        <ConversationList conversations={data} />
      )}
    </div>
  );
}

function SearchResults({ query, debounced, conversations }: { query: string; debounced: string; conversations: Conversation[] }) {
  const matched = conversations.filter((c) => matchesConversation(c, query));
  const { data: users, isFetching } = useUserSearch(debounced);
  // 이미 대화 중인 사람과 나는 ‘새 메시지’에서 뺍니다.
  const talking = new Set(conversations.map((c) => c.partner.id));
  const others = (users ?? []).filter((u) => !u.isMe && !talking.has(u.id));
  const waiting = debounced !== query || isFetching;

  if (!waiting && matched.length === 0 && others.length === 0) {
    return <EmptyState title={`‘${query}’와 일치하는 대화나 사람이 없어요`} />;
  }

  return (
    <div aria-live="polite">
      {matched.length > 0 && (
        <>
          <SectionTitle className="px-5 pt-1 pb-1">대화 {matched.length}</SectionTitle>
          <ConversationList conversations={matched} />
        </>
      )}
      {(others.length > 0 || waiting) && (
        <>
          <SectionTitle className="px-5 pt-4 pb-1">새 메시지 보내기</SectionTitle>
          {waiting && others.length === 0 ? (
            <div className="flex justify-center py-4">
              <Spinner className="size-5" label="사람 찾는 중" />
            </div>
          ) : (
            <ul>
              {others.map((u) => (
                <li key={u.id}>
                  <StartRow user={u} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

/** 아직 대화하지 않은 사람: 누르면 대화방을 만들고 바로 이동 */
function StartRow({ user }: { user: User }) {
  const start = useStartConversation();
  const router = useRouter();
  const toast = useToast();
  return (
    <button
      type="button"
      disabled={start.isPending}
      onClick={() =>
        start.mutate(user.username, {
          onSuccess: (c) => router.push(routes.conversation(c.id)),
          onError: (err) => toast({ message: errorMessage(err) }),
        })
      }
      className="flex w-full items-center gap-3 px-5 py-2.5 text-left active:bg-fill disabled:opacity-60"
    >
      <Avatar src={user.profileImageUrl} name={user.nickname} size={44} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-bold">{user.nickname}</span>
        <span className="block truncate text-[12px] text-ink-sub">
          @{user.username} · {user.region}
        </span>
      </span>
      <span className="shrink-0 text-[12px] font-bold text-primary-deep">메시지</span>
    </button>
  );
}

function ConversationList({ conversations }: { conversations: Conversation[] }) {
  return (
    <ul>
      {conversations.map((c) => {
        const unread = c.unreadCount > 0;
        return (
          <li key={c.id}>
            <Link href={routes.conversation(c.id)} className="flex items-center gap-3 px-5 py-3 active:bg-fill">
              <Avatar src={c.partner.profileImageUrl} name={c.partner.nickname} size={48} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="truncate text-[15px] font-bold">{c.partner.nickname}</span>
                  <span className="shrink-0 text-[12px] text-ink-faint">{formatRelative(c.updatedAt)}</span>
                </div>
                <p className={cn("mt-0.5 truncate text-[13px]", unread ? "font-bold text-ink" : "text-ink-sub")}>
                  {c.lastMessage?.isMine && "나: "}
                  {messagePreview(c.lastMessage)}
                </p>
              </div>
              {unread && (
                <span
                  className="flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-primary px-1.5 text-[11px] font-bold text-on-primary tabular-nums"
                  aria-label={`안 읽은 메시지 ${c.unreadCount}개`}
                >
                  {c.unreadCount}
                </span>
              )}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
