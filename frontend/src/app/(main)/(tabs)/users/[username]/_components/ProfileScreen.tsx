"use client";

import { Menu } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { CollectionCard, useUserCollections } from "@/entities/collection";
import { useMyVisitRecords } from "@/entities/plan";
import { PostGrid, useUserPosts } from "@/entities/post";
import { useUser } from "@/entities/user";
import { FollowButton } from "@/features/follow-user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { Avatar, EmptyState, ErrorState, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { SideSheet } from "@/shared/ui/SideSheet";
import { SettingsMenu } from "../../../../_components/SettingsMenu";
import {
  matchesVisibility,
  parseVisibilityFilter,
  VisibilityFilter,
  visibilityFilterLabel,
  type VisibilityFilterValue,
} from "./VisibilityFilter";
import { VisitMap } from "./VisitMap";

type Tab = "posts" | "collections" | "map" | "visits";

const BASE_TABS: { value: Tab; label: string }[] = [
  { value: "posts", label: "게시물" },
  { value: "collections", label: "컬렉션" },
  { value: "map", label: "방문 지도" },
];
/** 방문 기록은 본인 프로필에만 보입니다. */
const MY_TABS: { value: Tab; label: string }[] = [...BASE_TABS, { value: "visits", label: "방문 기록" }];

export function ProfileScreen({ username }: { username: string }) {
  const { data: user, isPending, isError, error } = useUser(username);
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  const tabs = user.isMe ? MY_TABS : BASE_TABS;
  const tab = tabs.find((t) => t.value === params.get("tab"))?.value ?? "posts";
  /** 공개 범위 필터: 내 프로필의 게시물 · 컬렉션 탭에서만 */
  const showFilter = user.isMe && (tab === "posts" || tab === "collections");
  const vis = showFilter ? parseVisibilityFilter(params.get("vis")) : "ALL";
  const setVis = (v: VisibilityFilterValue) =>
    router.replace(`${pathname}?tab=${tab}${v === "ALL" ? "" : `&vis=${v}`}`, { scroll: false });

  return (
    <div>
      <TopBar
        title={user.isMe ? "내 프로필" : user.nickname}
        hideBack={user.isMe}
        right={
          user.isMe && (
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="메뉴"
              aria-haspopup="dialog"
              aria-expanded={menuOpen}
              className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill"
            >
              <Menu size={23} strokeWidth={1.6} />
            </button>
          )
        }
      />
      <section className="px-5 pt-2">
        <div className="flex items-center gap-4">
          <Avatar src={user.profileImageUrl} name={user.nickname} size={72} />
          <div className="min-w-0 flex-1">
            <h2 className="text-[20px] font-bold">{user.nickname}</h2>
            <div className="mt-1 flex gap-3 text-[13px]">
              <span>
                게시물 <b>{user.postCount}</b>
              </span>
              <Link href={routes.follows(user.username, "followers")}>
                팔로워 <b>{user.followerCount.toLocaleString()}</b>
              </Link>
              <Link href={routes.follows(user.username, "following")}>
                팔로잉 <b>{user.followingCount.toLocaleString()}</b>
              </Link>
            </div>
          </div>
        </div>
        <p className="mt-3 text-[14px] leading-relaxed whitespace-pre-line text-ink-sub">{user.bio}</p>
        <div className="mt-4">
          {user.isMe ? (
            <div className="grid grid-cols-2 gap-2">
              <ButtonLink href={routes.editProfile()} variant="secondary" size="md">
                프로필 편집
              </ButtonLink>
              <Button variant="secondary" size="md" onClick={() => setMenuOpen(true)}>
                계정 설정
              </Button>
            </div>
          ) : (
            <FollowButton user={user} size="md" block />
          )}
        </div>
      </section>

      <div className="mt-5 flex items-center gap-2 border-b border-line px-5">
        <Tabs
          items={tabs}
          value={tab}
          onChange={(t) => router.replace(`${pathname}?tab=${t}`, { scroll: false })}
          className="min-w-0 flex-1"
        />
        {showFilter && <VisibilityFilter value={vis} onChange={setVis} />}
      </div>

      {tab === "posts" && <PostsTab username={username} isMe={user.isMe} vis={vis} />}
      {tab === "collections" && <CollectionsTab username={username} isMe={user.isMe} nickname={user.nickname} vis={vis} />}
      {tab === "map" && <VisitMap user={user} />}
      {tab === "visits" && user.isMe && <VisitsTab />}

      {user.isMe && (
        <SideSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={`${user.nickname}님의 계정`}>
          <SettingsMenu onNavigate={() => setMenuOpen(false)} />
        </SideSheet>
      )}
    </div>
  );
}

function PostsTab({ username, isMe, vis }: { username: string; isMe: boolean; vis: VisibilityFilterValue }) {
  const { data, isPending } = useUserPosts(username);
  if (isPending) return <Skeleton className="m-5 h-40" />;
  if (!data?.length) return <EmptyState title="아직 게시물이 없어요" />;
  const posts = data.filter((p) => matchesVisibility(vis, p.visibility));
  if (!posts.length) return <EmptyState title={`${visibilityFilterLabel(vis)} 게시물이 없어요`} />;
  return (
    <div className="pt-0.5">
      {vis !== "ALL" && (
        <p className="px-5 pt-2.5 pb-2 text-[13px] text-ink-sub">
          {visibilityFilterLabel(vis)} 게시물 <b className="text-ink">{posts.length}</b>
        </p>
      )}
      <PostGrid posts={posts} showVisibility={isMe} />
    </div>
  );
}

function CollectionsTab({
  username,
  isMe,
  nickname,
  vis,
}: {
  username: string;
  isMe: boolean;
  nickname: string;
  vis: VisibilityFilterValue;
}) {
  const { data, isPending } = useUserCollections(username);
  if (isPending) return <Skeleton className="m-5 h-40" />;
  if (!data?.length) return <EmptyState title={isMe ? "아직 컬렉션이 없어요" : "공개 컬렉션이 없어요"} />;
  const collections = data.filter((c) => matchesVisibility(vis, c.visibility));
  if (!collections.length) return <EmptyState title={`${visibilityFilterLabel(vis)} 컬렉션이 없어요`} />;
  return (
    <div className="px-5">
      <p className="pt-3 text-[13px] font-bold">
        {isMe ? (vis === "ALL" ? "내 컬렉션" : `내 컬렉션 · ${visibilityFilterLabel(vis)}`) : `${nickname}님의 공개 컬렉션`}{" "}
        {collections.length}
      </p>
      <div className="divide-y divide-line">
        {collections.map((c) => (
          <CollectionCard key={c.id} collection={c} />
        ))}
      </div>
    </div>
  );
}

/** 내 방문 기록: 일정을 마치고 실제로 다녀온 장소 (본인만 볼 수 있음) */
function VisitsTab() {
  const { data, isPending } = useMyVisitRecords();
  if (isPending) return <Skeleton className="m-5 h-40" />;
  if (!data?.length) return <EmptyState title="아직 방문 기록이 없어요" description="일정을 마치고 방문 기록을 남겨 보세요." />;
  return (
    <ul className="divide-y divide-line px-5">
      {data.map((v) => (
        <li key={v.planId}>
          <Link href={routes.plan(v.planId)} className="flex items-center gap-4 py-3">
            <Thumb src={v.coverImageUrl} alt="" className="size-[72px] rounded-card" />
            <div className="min-w-0">
              <p className="text-[15px] font-bold">
                {formatShortDate(v.date)} {v.title}
              </p>
              <p className="mt-0.5 text-[12px] text-ink-sub">
                {v.region} · {v.visitedCount}곳 방문 · {v.places.map((p) => p.name).join(", ")}
              </p>
              <p className="mt-1 text-[12px] font-bold text-primary-deep">기록 보기 →</p>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
