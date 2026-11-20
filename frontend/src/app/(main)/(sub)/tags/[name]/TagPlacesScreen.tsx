"use client";

import Link from "next/link";
import { useMemo } from "react";
import { PlaceListItem } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useTagPlaces } from "../_api/tagApi";

/** 태그별 장소: 지도 + 목록. 함께 단 다른 태그로 옮겨 갈 수 있어요. */
export function TagPlacesScreen({ name }: { name: string }) {
  const { data, isPending, isError, error, refetch } = useTagPlaces(name);
  const markers = useMemo(() => (data ?? []).map((t) => ({ id: t.place.id, position: t.place.location, title: t.place.name })), [data]);

  return (
    <div>
      <TopBar title={`#${name}`} backHref={routes.tags()} />
      {isPending ? (
        <Skeleton className="m-5 h-60" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title="이 태그를 단 장소가 없어요" description="태그를 지웠거나, 장소를 내 지도에서 뺐을 수 있어요." />
      ) : (
        <>
          <MapView className="h-52" markers={markers} />
          <p className="px-5 pt-4 pb-1 text-[13px] text-ink-sub">저장한 장소 {data.length}곳</p>
          <ul className="divide-y divide-line px-5 pb-8">
            {data.map((t) => (
              <li key={t.savedPlaceId} className="py-1">
                <Link href={routes.place(t.place.id)} className="block">
                  <PlaceListItem place={t.place} meta={t.memo || undefined} />
                </Link>
                <div className="flex flex-wrap gap-1.5 pb-2 pl-[68px]">
                  {t.tags.map((tag) =>
                    tag === name ? (
                      <span key={tag} className="rounded-full bg-selected px-2 py-0.5 text-[12px] font-bold text-primary-deep">
                        #{tag}
                      </span>
                    ) : (
                      <Link key={tag} href={routes.tag(tag)} className="rounded-full bg-fill px-2 py-0.5 text-[12px] text-ink-sub">
                        #{tag}
                      </Link>
                    ),
                  )}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
