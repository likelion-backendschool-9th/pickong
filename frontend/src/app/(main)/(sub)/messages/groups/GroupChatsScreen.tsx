"use client";

import { BellOff, Crown, Plus, Users } from "lucide-react";
import Link from "next/link";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { Avatar, EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useGroupChats, type GroupChat } from "./_api/groupChatApi";

export function GroupChatsScreen() {
  const { data, isPending, isError, error, refetch } = useGroupChats();

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
      ) : data.length === 0 ? (
        <EmptyState icon={<Users size={24} />} title="참여 중인 그룹 채팅이 없어요" description="함께 갈 친구들을 모아 일정과 장소를 나눠 보세요.">
          <ButtonLink href={routes.newGroupChat()}>그룹 채팅 만들기</ButtonLink>
        </EmptyState>
      ) : (
        <ul className="px-5">
          {data.map((g) => (
            <li key={g.id}>
              <GroupRow g={g} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function GroupRow({ g }: { g: GroupChat }) {
  const faces = g.members.filter((m) => !m.isMe).slice(0, 2);
  return (
    <Link href={routes.groupChat(g.id)} className="-mx-5 flex items-center gap-3 px-5 py-3 active:bg-fill">
      <span className="relative size-12 shrink-0" aria-hidden>
        {faces[0] && (
          <span className="absolute top-0 left-0">
            <Avatar src={faces[0].profileImageUrl} name={faces[0].nickname} size={32} />
          </span>
        )}
        {faces[1] && (
          <span className="absolute right-0 bottom-0 rounded-full ring-2 ring-bg">
            <Avatar src={faces[1].profileImageUrl} name={faces[1].nickname} size={32} />
          </span>
        )}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-bold">{g.name}</span>
          <span className="text-[13px] text-ink-faint">{g.members.length}</span>
          {g.isOwner && <Crown size={13} className="text-primary-deep" aria-label="방장" />}
          {g.muted && <BellOff size={13} className="text-ink-faint" aria-label="알림 꺼짐" />}
        </div>
        <p className="truncate text-[12px] text-ink-sub">{g.members.map((m) => m.nickname).join(", ")}</p>
      </div>
      <span className="text-[11px] text-ink-faint">{formatRelative(g.updatedAt)}</span>
    </Link>
  );
}
