"use client";

import { Bell, List, Map as MapIcon, Search } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useFeed, postKeys, type FeedTab } from "@/entities/post";
import { useQueryClient } from "@tanstack/react-query";
import { PostCard } from "@/widgets/post-card";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { HOME_RESELECT_EVENT } from "@/shared/lib/events";
import { ButtonLink } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { PullToRefresh } from "@/shared/ui/PullToRefresh";
import { FeedMap } from "./FeedMap";
import { RegionDropdown } from "./RegionDropdown";

/**
 * 홈 피드. 목록 · 지도 전환은 주소(?view=map)에 남기므로, 하단바 홈(/feed)을 누르면 언제나 피드 목록으로 돌아옵니다.
 */
export function FeedScreen() {
  const [tab, setTab] = useState<FeedTab>("following");
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const mode = params.get("view") === "map" ? "map" : "list";
  /** 쉼표 구분 지역 목록. "" = 전체 */
  const region = params.get("region") ?? "";

  const setMode = (m: "list" | "map") => {
    const sp = new URLSearchParams(params.toString());
    if (m === "map") sp.set("view", "map"); else sp.delete("view");
    router.replace(`${pathname}?${sp.toString()}`.replace(/\?$/, ""), { scroll: false });
  };

  const setRegion = (r: string) => {
    const sp = new URLSearchParams(params.toString());
    if (!r) sp.delete("region"); else sp.set("region", r);
    router.replace(`${pathname}?${sp.toString()}`.replace(/\?$/, ""), { scroll: false });
  };

  return (
    <div>
      <header className="sticky top-0 z-30 bg-bg">
        <div className="flex h-14 items-center pr-3 pl-5">
          {/* 워드마크: Noto Sans KR Black 30px, 자간 -2.1px + 연두 점 */}
          <h1 className="flex items-end text-[30px] leading-[45px] font-black tracking-[-2.1px] text-ink">
            찜콩
            <span className="mb-[11px] ml-[3px] size-[6px] rounded-full bg-primary" aria-hidden />
          </h1>
          <div className="ml-auto flex items-center gap-1">
            <Link href={routes.search()} aria-label="검색" className="inline-flex size-10 items-center justify-center rounded-full text-ink active:bg-fill">
              <Search size={21} strokeWidth={1.8} />
            </Link>
            <Link href={routes.notifications()} aria-label="알림, 새 알림 있음" className="relative inline-flex size-10 items-center justify-center rounded-full text-ink active:bg-fill">
              <Bell size={21} strokeWidth={1.8} />
              <span className="absolute top-[9px] right-[10px] size-[6px] rounded-full bg-primary" aria-hidden />
            </Link>
          </div>
        </div>
        <div className="flex items-center border-b border-line px-5">
          <Tabs
            items={[
              { value: "following", label: "팔로잉" },
              { value: "recommended", label: "추천" },
            ]}
            value={tab}
            onChange={(t) => { setTab(t); if (t === "following") setRegion(""); }}
          />
          {/* 추천 탭 바로 옆에 위치 */}
          {tab === "recommended" && (
            <div className="ml-2">
              <RegionDropdown value={region} onChange={setRegion} />
            </div>
          )}
          <div className="ml-auto">
            <button
              type="button"
              onClick={() => setMode(mode === "map" ? "list" : "map")}
              aria-pressed={mode === "map"}
              className="inline-flex h-7 items-center gap-1.5 rounded-full bg-accent-soft px-3 text-[12px] font-bold text-primary-deep active:brightness-95"
            >
              {mode === "map" ? <List size={14} /> : <MapIcon size={14} />}
              {mode === "map" ? "목록" : "지도"}
            </button>
          </div>
        </div>
      </header>

      {mode === "map" ? (
        <FeedMap tab={tab} region={tab === "recommended" && region ? region : undefined} />
      ) : (
        <FeedList tab={tab} region={tab === "recommended" && region ? region : undefined} />
      )}
    </div>
  );
}

function FeedList({ tab, region }: { tab: FeedTab; region?: string }) {
  const regionParam = region || undefined;
  const qc = useQueryClient();
  const {
    data,
    isPending,
    isError,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useFeed(tab, regionParam);
  const [refreshSignal, setRefreshSignal] = useState(0);

  // 페이지들을 하나의 배열로 합침
  const posts = data?.pages.flatMap((p) => p.items) ?? [];

  // 맨 아래 감지용 ref — 이 div가 화면에 보이면 다음 페이지 요청
  const bottomRef = useRef<HTMLDivElement>(null);

  // IntersectionObserver: bottomRef가 뷰포트에 들어오면 fetchNextPage 호출
  useEffect(() => {
    const el = bottomRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      (entries) => {
        // 보이고 + 다음 페이지 있고 + 현재 로딩 중 아닐 때만 요청
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage();
        }
      },
      { threshold: 0.1 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  // 피드에서 홈 버튼을 다시 누르면: 맨 위로 올라간 뒤 당겨서 새로고침한 것처럼 새로고침
  useEffect(() => {
    const onReselect = () => {
      window.scrollTo({ top: 0, behavior: "smooth" });
      const started = Date.now();
      const wait = () => {
        if (window.scrollY <= 1 || Date.now() - started > 800) setRefreshSignal((n) => n + 1);
        else requestAnimationFrame(wait);
      };
      requestAnimationFrame(wait);
    };
    window.addEventListener(HOME_RESELECT_EVENT, onReselect);
    return () => window.removeEventListener(HOME_RESELECT_EVENT, onReselect);
  }, []);

  // pull-to-refresh: 캐시를 초기화하고 1페이지부터 다시 불러옴
  // resetQueries는 Promise를 반환해 스피너가 데이터 로드 완료까지 유지됨
  const handleRefresh = () =>
    qc.resetQueries({ queryKey: postKeys.feed(tab, regionParam), exact: true });

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

  return (
    <PullToRefresh onRefresh={handleRefresh} refreshSignal={refreshSignal}>
      {isError ? (
        <ErrorState message={errorMessage(error)} onRetry={handleRefresh} />
      ) : posts.length === 0 ? (
        <EmptyState title="아직 볼 게시물이 없어요" description="추천 탭에서 마음에 드는 사람을 팔로우해 보세요.">
          <ButtonLink href={routes.search()} variant="secondary" block>
            사람 · 장소 찾아보기
          </ButtonLink>
        </EmptyState>
      ) : (
        <>
          <div className="divide-y divide-line">
            {posts.map((post, i) => (
              <PostCard key={post.id} post={post} priority={i === 0} />
            ))}
          </div>
          {/* 다음 페이지 로딩 스피너 */}
          {isFetchingNextPage && (
            <div className="space-y-3 py-3">
              <Skeleton className="aspect-[13/10] w-full rounded-none" />
            </div>
          )}
          {/* 이 div가 화면에 들어오면 IntersectionObserver가 다음 페이지 요청 */}
          <div ref={bottomRef} className="h-1" />
        </>
      )}
    </PullToRefresh>
  );
}
