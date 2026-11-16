"use client";

import { Bell, List, Map as MapIcon, Search } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useFeed, type FeedTab } from "@/entities/post";
import { PostCard } from "@/widgets/post-card";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { ButtonLink } from "@/shared/ui/Button";
import { Chip, Tabs } from "@/shared/ui/controls";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { PullToRefresh } from "@/shared/ui/PullToRefresh";
import { FeedMap } from "./FeedMap";

export function FeedScreen() {
  const [tab, setTab] = useState<FeedTab>("following");
  const [mode, setMode] = useState<"list" | "map">("list");

  return (
    <div>
      <header className="sticky top-0 z-30 bg-bg">
        <div className="flex h-[52px] items-center px-4">
          <h1 className="text-[24px] font-bold tracking-tight">찜콩</h1>
          <div className="ml-auto flex">
            <Link href={routes.search()} aria-label="검색" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
              <Search size={22} strokeWidth={1.6} />
            </Link>
            <Link href={routes.notifications()} aria-label="알림" className="relative inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
              <Bell size={22} strokeWidth={1.6} />
              <span className="absolute top-2.5 right-2.5 size-2 rounded-full bg-primary" aria-label="새 알림" />
            </Link>
          </div>
        </div>
        <div className="flex items-center border-b border-line px-4">
          <Tabs
            items={[
              { value: "following", label: "팔로잉" },
              { value: "recommended", label: "추천" },
            ]}
            value={tab}
            onChange={setTab}
          />
          <Chip
            className="ml-auto"
            selected={mode === "map"}
            onClick={() => setMode(mode === "map" ? "list" : "map")}
            icon={mode === "map" ? <List size={14} /> : <MapIcon size={14} />}
          >
            {mode === "map" ? "목록" : "지도"}
          </Chip>
        </div>
      </header>

      {mode === "map" ? <FeedMap tab={tab} /> : <FeedList tab={tab} />}
    </div>
  );
}

function FeedList({ tab }: { tab: FeedTab }) {
  const { data: posts, isPending, isError, error, refetch } = useFeed(tab);

  if (isPending) {
    return (
      <div className="space-y-3 py-3">
        <div className="flex items-center gap-2.5 px-4">
          <Skeleton className="size-9 rounded-full" />
          <Skeleton className="h-4 w-24" />
        </div>
        <Skeleton className="aspect-[13/10] w-full rounded-none" />
        <Skeleton className="mx-4 h-4 w-2/3" />
      </div>
    );
  }
  // 위에서 당겼다 놓으면 피드를 다시 불러옵니다 (오류 · 빈 화면에서도 가능)
  return (
    <PullToRefresh onRefresh={() => refetch()}>
      {isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : posts.length === 0 ? (
        <EmptyState title="아직 볼 게시물이 없어요" description="추천 탭에서 마음에 드는 사람을 팔로우해 보세요.">
          <ButtonLink href={routes.search()} variant="secondary" block>
            사람 · 장소 찾아보기
          </ButtonLink>
        </EmptyState>
      ) : (
        <div className="divide-y divide-line">
          {posts.map((post, i) => (
            <PostCard key={post.id} post={post} priority={i === 0} />
          ))}
        </div>
      )}
    </PullToRefresh>
  );
}
