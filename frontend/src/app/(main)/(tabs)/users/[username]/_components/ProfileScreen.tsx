"use client";

import { ChevronDown, List, Map as MapIcon, Menu } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { CollectionCard, useUserCollections } from "@/entities/collection";
import { useStartConversation } from "@/entities/conversation";
import { PostGrid, PostPlaceRow, useUserPosts } from "@/entities/post";
import { useUser, type User } from "@/entities/user";
import { FollowButton } from "@/features/follow-user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Chip, Tabs } from "@/shared/ui/controls";
import { Avatar, EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { PageHeader, TopBar } from "@/shared/ui/layout";
import { SideSheet } from "@/shared/ui/SideSheet";
import { SettingsMenu } from "../../../../_components/SettingsMenu";
import {
  matchesVisibility,
  parseVisibilityFilter,
  VisibilityFilter,
  visibilityFilterLabel,
  type VisibilityFilterValue,
} from "./VisibilityFilter";
import { PlansTab } from "./PlansTab";
import { VisitMap } from "./VisitMap";

type Tab = "posts" | "collections" | "visits" | "plans";

const BASE_TABS: { value: Tab; label: string }[] = [
  { value: "posts", label: "게시물" },
  { value: "collections", label: "컬렉션" },
  { value: "visits", label: "방문" },
];
/** 일정(지난 일정 · 방문 기록 포함)은 본인만 볼 수 있어서 내 프로필에만 있습니다. */
const MY_TABS: { value: Tab; label: string }[] = [...BASE_TABS, { value: "plans", label: "일정" }];

export function ProfileScreen({ username }: { username: string }) {
  const { data: user, isPending, isError, error } = useUser(username);
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const startConversation = useStartConversation();

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  const tabs = user.isMe ? MY_TABS : BASE_TABS;
  const tab = tabs.find((t) => t.value === params.get("tab"))?.value ?? "posts";
  /** 공개 범위 필터: 내 프로필의 게시물 · 컬렉션 탭에서만 */
  const showFilter = user.isMe && (tab === "posts" || tab === "collections");
  const vis = showFilter ? parseVisibilityFilter(params.get("vis")) : "ALL";
  const setVis = (v: VisibilityFilterValue) =>
    router.replace(`${pathname}?tab=${tab}${v === "ALL" ? "" : `&vis=${v}`}`, { scroll: false });
  /** 방문 탭: 기록(목록) ↔ 지도 */
  const visitView = params.get("view") === "map" ? "map" : "list";
  const setVisitView = (v: "list" | "map") =>
    router.replace(`${pathname}?tab=visits${v === "map" ? "&view=map" : ""}`, { scroll: false });

  return (
    <div>
      {user.isMe ? (
        // 내 프로필: 영문 라벨 + 아이디(Inter) 제목. 아이디 옆 ⌄와 오른쪽 ≡은 계정 메뉴를 엽니다.
        <header className="sticky top-0 z-30 bg-bg">
          <PageHeader
            label="Profile"
            variant="latin"
            title={user.username}
            titleAddon={
              <button
                type="button"
                onClick={() => setMenuOpen(true)}
                aria-label="계정 메뉴 열기"
                aria-haspopup="dialog"
                className="inline-flex size-7 items-center justify-center rounded-full text-ink-sub active:bg-fill"
              >
                <ChevronDown size={18} strokeWidth={2} />
              </button>
            }
            right={
              <button
                type="button"
                onClick={() => setMenuOpen(true)}
                aria-label="메뉴"
                aria-haspopup="dialog"
                aria-expanded={menuOpen}
                className="inline-flex size-10 items-center justify-center rounded-full text-ink active:bg-fill"
              >
                <Menu size={23} strokeWidth={1.7} />
              </button>
            }
          />
        </header>
      ) : (
        <TopBar title={user.nickname} />
      )}
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
            <div className="grid grid-cols-2 gap-2">
              <FollowButton user={user} size="md" block />
              <Button
                variant="secondary"
                size="md"
                disabled={startConversation.isPending}
                onClick={() =>
                  startConversation.mutate(user.username, { onSuccess: (c) => router.push(routes.conversation(c.id)) })
                }
              >
                메시지
              </Button>
            </div>
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
        {tab === "visits" && (
          <Chip
            className="shrink-0"
            selected={visitView === "map"}
            onClick={() => setVisitView(visitView === "map" ? "list" : "map")}
            icon={visitView === "map" ? <List size={14} /> : <MapIcon size={14} />}
          >
            {visitView === "map" ? "기록" : "지도"}
          </Chip>
        )}
      </div>

      {tab === "posts" && <PostsTab username={username} isMe={user.isMe} vis={vis} />}
      {tab === "collections" && <CollectionsTab username={username} isMe={user.isMe} nickname={user.nickname} vis={vis} />}
      {tab === "visits" && (visitView === "map" ? <VisitMap user={user} /> : <VisitList user={user} />)}
      {tab === "plans" && user.isMe && <PlansTab />}

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

/**
 * 방문 탭의 기록(목록) 보기: 장소를 연결한 게시물을 최신순으로 보여 줍니다.
 * 지도 보기(VisitMap)와 같은 데이터(게시물에 연결된 장소)입니다.
 */
function VisitList({ user }: { user: User }) {
  const { data, isPending } = useUserPosts(user.username);
  if (isPending) return <Skeleton className="m-5 h-40" />;
  const posts = (data ?? []).filter((p) => p.places.length > 0);
  if (!posts.length) {
    return (
      <EmptyState
        title="아직 방문 기록이 없어요"
        description={user.isMe ? "게시물 사진에 장소를 연결하면 여기에 쌓여요." : undefined}
      />
    );
  }
  const placeCount = new Set(posts.flatMap((p) => p.places.map((pl) => pl.id))).size;
  return (
    <div>
      <p className="px-5 pt-3 pb-1 text-[13px] text-ink-sub">
        게시물에 남긴 장소 <b className="text-ink">{placeCount}</b>곳 · 게시물 <b className="text-ink">{posts.length}</b>개
      </p>
      <ul className="divide-y divide-line">
        {posts.map((post) => (
          <li key={post.id}>
            <PostPlaceRow post={post} showAuthor={false} />
          </li>
        ))}
      </ul>
    </div>
  );
}
