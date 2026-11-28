"use client";

import { BellOff, Plus, Users } from "lucide-react";
import Link from "next/link";
import { ConversationAvatar, useConversations, type Conversation } from "@/entities/conversation";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";

/** 그룹 채팅만 모아 보기. 그룹은 DM 목록에도 함께 나옵니다 (같은 대화 목록을 걸러서 씀) */
export function GroupChatsScreen() {
  const { data, isPending, isError, error, refetch } = useConversations();
  const groups = (data ?? []).filter((c) => c.type === "GROUP");

  return (
    <div>
      <TopBar
        title="그룹 채팅"
        backHref={routes.messages()}
        right={
          <Link href={routes.newGroupChat()} aria-label="그룹 채팅 만들기" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
            <Plus size={22} strokeWidth={1.8} />
          </Link>
        }
      />
      {isPending ? (
        <Skeleton className="m-5 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : groups.length === 0 ? (
        <EmptyState icon={<Users size={24} />} title="참여 중인 그룹 채팅이 없어요" description="함께 갈 친구들을 모아 일정과 장소를 나눠 보세요.">
          <ButtonLink href={routes.newGroupChat()}>그룹 채팅 만들기</ButtonLink>
        </EmptyState>
      ) : (
        <ul className="px-5">
          {groups.map((g) => (
            <li key={g.id}>
              <GroupRow g={g} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function GroupRow({ g }: { g: Conversation }) {
  return (
    <Link href={routes.conversation(g.id)} className="-mx-5 flex items-center gap-3 px-5 py-3 active:bg-fill">
      <ConversationAvatar conversation={g} size={48} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-bold">{g.title}</span>
          <span className="text-[13px] text-ink-faint tabular-nums">{g.memberCount}</span>
          {g.muted && <BellOff size={13} className="text-ink-faint" aria-label="알림 꺼짐" />}
        </div>
        <p className="truncate text-[12px] text-ink-sub">{g.members.map((m) => m.username).join(", ")}</p>
      </div>
      <span className="text-[11px] text-ink-faint">{formatRelative(g.updatedAt)}</span>
    </Link>
  );
}
