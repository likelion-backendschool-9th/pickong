"use client";

import { useMe, useFollowing } from "@/entities/user";
import { CheckMark } from "@/shared/ui/controls";
import { Avatar, EmptyState, Skeleton } from "@/shared/ui/display";
import { toggleId } from "../_lib/group";

/** 내가 팔로우하는 사람 중에서 초대할 사람 고르기. 이미 들어와 있는 사람은 빼고 보여 줍니다. */
export function MemberPicker({ selected, onChange, excludeIds = [], max }: { selected: string[]; onChange: (ids: string[]) => void; excludeIds?: string[]; max: number }) {
  const { data: me } = useMe();
  const { data: following, isPending } = useFollowing(me?.username ?? "");
  const candidates = (following ?? []).filter((u) => !excludeIds.includes(u.id));

  if (!me || isPending) return <Skeleton className="h-40" />;
  if (candidates.length === 0) return <EmptyState title="초대할 수 있는 사람이 없어요" description="팔로우하는 사람만 그룹 채팅에 초대할 수 있어요." />;

  return (
    <ul className="divide-y divide-line">
      {candidates.map((u) => {
        const on = selected.includes(u.id);
        const disabled = !on && selected.length >= max;
        return (
          <li key={u.id}>
            <button
              type="button"
              role="checkbox"
              aria-checked={on}
              disabled={disabled}
              onClick={() => onChange(toggleId(selected, u.id))}
              className="flex w-full items-center gap-3 py-2.5 text-left disabled:opacity-40"
            >
              <Avatar src={u.profileImageUrl} name={u.username} size={40} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[14px] font-bold">{u.username}</span>
              </span>
              <CheckMark checked={on} round />
            </button>
          </li>
        );
      })}
    </ul>
  );
}
