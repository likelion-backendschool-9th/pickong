"use client";

import { Settings } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { CollectionCard, useUserCollections } from "@/entities/collection";
import { useMyVisitRecords } from "@/entities/plan";
import { PostGrid, useUserPosts } from "@/entities/post";
import { useUser } from "@/entities/user";
import { FollowButton } from "@/features/follow-user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { Avatar, EmptyState, ErrorState, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
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

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  const tabs = user.isMe ? MY_TABS : BASE_TABS;
  const tab = tabs.find((t) => t.value === params.get("tab"))?.value ?? "posts";

  return (
    <div>
      <TopBar
        title={user.isMe ? "내 프로필" : user.nickname}
        hideBack={user.isMe}
        right={
          user.isMe && (
            <Link href={routes.settings()} aria-label="계정 설정" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
              <Settings size={21} strokeWidth={1.6} />
            </Link>
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
              <ButtonLink href={routes.settings()} variant="secondary" size="md">
                계정 설정
              </ButtonLink>
            </div>
          ) : (
            <FollowButton user={user} size="md" block />
          )}
        </div>
      </section>

      <div className="mt-5 border-b border-line px-5">
        <Tabs
          items={tabs}
          value={tab}
          onChange={(t) => router.replace(`${pathname}?tab=${t}`, { scroll: false })}
        />
      </div>

      {tab === "posts" && <PostsTab username={username} />}
      {tab === "collections" && <CollectionsTab username={username} isMe={user.isMe} nickname={user.nickname} />}
      {tab === "map" && <VisitMap user={user} />}
      {tab === "visits" && user.isMe && <VisitsTab />}
    </div>
  );
}

function PostsTab({ username }: { username: string }) {
  const { data, isPending } = useUserPosts(username);
  if (isPending) return <Skeleton className="m-5 h-40" />;
  if (!data?.length) return <EmptyState title="아직 게시물이 없어요" />;
  return <div className="pt-0.5"><PostGrid posts={data} /></div>;
}

function CollectionsTab({ username, isMe, nickname }: { username: string; isMe: boolean; nickname: string }) {
  const { data, isPending } = useUserCollections(username);
  if (isPending) return <Skeleton className="m-5 h-40" />;
  if (!data?.length) return <EmptyState title="공개 컬렉션이 없어요" />;
  return (
    <div className="px-5">
      <p className="pt-3 text-[13px] font-bold">
        {isMe ? "내 컬렉션" : `${nickname}님의 공개 컬렉션`} {data.length}
      </p>
      <div className="divide-y divide-line">
        {data.map((c) => (
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
