"use client";

import { X } from "lucide-react";
import { useMemo, useState } from "react";
import { categoryLabel } from "@/entities/place";
import { PostPlaceRow, useFeed, useFeedPlaces, type FeedTab } from "@/entities/post";
import { SavePlaceButton } from "@/features/save-place";
import { inBounds, type LatLngBounds } from "@/shared/lib/geo";
import { MapView } from "@/shared/map";
import { EmptyState, Skeleton, Thumb } from "@/shared/ui/display";

const TAB_LABEL: Record<FeedTab, string> = { following: "팔로잉", recommended: "추천" };

/**
 * 피드 지도: 지금 탭(팔로잉 · 추천)의 게시물에 연결된 장소를 지도에 띄우고,
 * 지도 화면에 보이는 지역의 게시물을 아래에 모두 보여 줍니다. 지도를 움직이면 목록도 바뀝니다.
 * 내가 저장한 장소를 관리하는 ‘내 지도’와는 다른 데이터입니다.
 */
export function FeedMap({ tab, region }: { tab: FeedTab; region?: string }) {
  const regionParam = region === "전국" ? undefined : region;
  const { data: places, isPending: placesPending } = useFeedPlaces(tab, regionParam);
  const { data: posts, isPending: postsPending } = useFeed(tab, regionParam);
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const selected = places?.find((d) => d.place.id === selectedId) ?? null;

  /** 지도에 보이는 장소 (장소를 고르면 그 장소만) */
  const visiblePlaces = useMemo(() => {
    if (!places) return [];
    if (selected) return [selected];
    return bounds ? places.filter((d) => inBounds(d.place.location, bounds)) : places;
  }, [places, bounds, selected]);

  /** 보이는 장소가 나온 게시물 (최신순, 중복 없이) + 그 게시물에서 보이는 장소 */
  const visiblePosts = useMemo(() => {
    if (!posts) return [];
    const placeById = new Map(visiblePlaces.map((d) => [d.place.id, d.place]));
    return posts
      .map((post) => ({ post, places: post.places.filter((p) => placeById.has(p.id)) }))
      .filter((row) => row.places.length > 0);
  }, [posts, visiblePlaces]);

  if (placesPending || postsPending) return <Skeleton className="h-[calc(100dvh-190px)] rounded-none" />;

  return (
    <div>
      <div className="relative">
        <div className="absolute top-3 left-3 z-10 rounded-full bg-white/95 px-3 py-1.5 text-[12px] font-bold text-ink shadow-sm">
          {TAB_LABEL[tab]} 게시물의 장소 {places?.length ?? 0}곳
        </div>
        <MapView
          className="h-[52dvh] min-h-[320px]"
          markers={(places ?? []).map((d) => ({
            id: d.place.id,
            position: d.place.location,
            label: String(d.postCount),
            title: d.place.name,
          }))}
          selectedId={selectedId}
          onMarkerClick={(id) => setSelectedId((cur) => (cur === id ? null : id))}
          onBoundsChange={setBounds}
          ariaLabel={`${TAB_LABEL[tab]} 피드 지도`}
        />
        {selected && (
          <div className="absolute inset-x-3 bottom-3 z-10 flex items-center gap-3 rounded-card bg-bg p-3 shadow-[0_2px_12px_rgba(0,0,0,0.12)]">
            <Thumb src={selected.place.imageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[15px] font-bold">{selected.place.name}</div>
              <div className="text-[12px] text-ink-sub">
                {selected.place.region} · {categoryLabel(selected.place.category)} · 게시물 {selected.postCount}개
              </div>
            </div>
            <SavePlaceButton place={selected.place} withLabel source={{ type: "POST", refId: selected.postIds[0], label: "피드 지도에서 발견" }} />
            <button
              type="button"
              onClick={() => setSelectedId(null)}
              aria-label="장소 선택 해제"
              className="-mr-1 inline-flex size-9 shrink-0 items-center justify-center rounded-full text-ink-sub active:bg-fill"
            >
              <X size={18} />
            </button>
          </div>
        )}
      </div>

      <section aria-live="polite">
        <h2 className="px-4 pt-4 pb-1 text-[14px] font-bold">
          {selected ? `${selected.place.name}의 게시물` : "지도에 보이는 지역의 게시물"}{" "}
          <span className="text-primary-deep">{visiblePosts.length}</span>
        </h2>
        {visiblePosts.length === 0 ? (
          <EmptyState
            title={tab === "following" ? "이 지역에는 팔로잉한 사람의 게시물이 없어요" : "이 지역에는 추천 게시물이 없어요"}
            description="지도를 움직이거나 축소해 보세요."
          />
        ) : (
          <ul className="divide-y divide-line">
            {visiblePosts.map(({ post, places: here }) => (
              <li key={post.id}>
                <PostPlaceRow post={post} places={here} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
