"use client";

import { Search } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { UserRow, useFollowers, useFollowing, useUser } from "@/entities/user";
import { FollowButton } from "@/features/follow-user";
import { routes } from "@/shared/config/routes";
import { Tabs } from "@/shared/ui/controls";
import { EmptyState, Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";

export function FollowsScreen({ username }: { username: string }) {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const tab = params.get("tab") === "following" ? "following" : "followers";
  const { data: user } = useUser(username);
  const followers = useFollowers(username);
  const following = useFollowing(username);
  const [q, setQ] = useState("");
  const list = (tab === "followers" ? followers : following).data;
  // 화면에는 아이디만 보이지만, 이름을 기억하는 경우도 찾을 수 있게 이름으로도 검색합니다.
  const filtered = list?.filter((u) => (u.username + (u.name ?? "")).includes(q.trim()));

  return (
    <div>
      <TopBar title={user?.username ?? ""} backHref={routes.user(username)} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "followers", label: `팔로워 ${user?.followerCount ?? ""}` },
            { value: "following", label: `팔로잉 ${user?.followingCount ?? ""}` },
          ]}
          value={tab}
          onChange={(t) => router.replace(`${pathname}?tab=${t}`, { scroll: false })}
        />
      </div>
      <div className="px-5 pt-3">
        <div className="relative">
          <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
          <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="아이디 · 이름 검색" aria-label="아이디 · 이름 검색" className="h-11 pl-10" />
        </div>
        {!filtered ? (
          <Skeleton className="mt-4 h-40" />
        ) : filtered.length === 0 ? (
          <EmptyState title={q ? "검색 결과가 없어요" : tab === "followers" ? "아직 팔로워가 없어요" : "아직 팔로우한 사람이 없어요"} />
        ) : (
          <ul className="mt-2">
            {filtered.map((u) => (
              <li key={u.id}>
                <UserRow user={u} trailing={<FollowButton user={u} />} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
