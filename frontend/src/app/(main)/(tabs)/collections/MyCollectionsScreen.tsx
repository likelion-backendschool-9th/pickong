"use client";

import { Plus } from "lucide-react";
import { CollectionCard, useMyCollections } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { ButtonLink } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";

/** 내 컬렉션: 장소를 주제별로 묶어 둡니다. */
export function MyCollectionsScreen() {
  const { data, isPending, isError, error, refetch } = useMyCollections();
  return (
    <div>
      <TopBar title="내 컬렉션" backHref={routes.map()} />
      <div className="px-5">
        <h2 className="pt-2 text-[20px] font-bold">나만의 기준으로 모아두세요</h2>
        <p className="mt-1 text-[13px] text-ink-sub">컬렉션을 지워도 저장한 장소는 내 지도에 남아요.</p>
        {isPending ? (
          <div className="mt-4 space-y-3">
            <Skeleton className="h-20" />
            <Skeleton className="h-20" />
          </div>
        ) : isError ? (
          <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
        ) : data.length === 0 ? (
          <EmptyState title="아직 컬렉션이 없어요" description="‘성수 카페’, ‘주말에 가볼 곳’처럼 묶어 보세요." />
        ) : (
          <div className="mt-2 divide-y divide-line">
            {data.map((c) => (
              <CollectionCard key={c.id} collection={c} />
            ))}
          </div>
        )}
        <ButtonLink href={routes.newCollection()} block className="mt-4 mb-6" icon={<Plus size={18} />}>
          컬렉션 만들기
        </ButtonLink>
      </div>
    </div>
  );
}
