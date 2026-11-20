"use client";

import { MapPinPlus, Pencil } from "lucide-react";
import Link from "next/link";
import { PlaceListItem } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { ButtonLink } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useMyPlaces } from "../_api/myPlaceApi";

/** 지도에서 좌표를 직접 지정해 만든 장소 목록. 수정 · 삭제는 각 장소의 수정 화면에서 합니다. */
export function MyPlacesScreen() {
  const { data, isPending, isError, error, refetch } = useMyPlaces();

  return (
    <div>
      <TopBar
        title="직접 등록한 장소"
        backHref={routes.settings()}
        right={
          <Link href={routes.newPlace()} aria-label="새 장소 등록" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
            <MapPinPlus size={21} strokeWidth={1.8} />
          </Link>
        }
      />
      {isPending ? (
        <Skeleton className="m-5 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState
          icon={<MapPinPlus size={24} />}
          title="직접 등록한 장소가 없어요"
          description="지도에 없는 골목 · 풍경 명소를 좌표로 직접 등록해 보세요."
        >
          <ButtonLink href={routes.newPlace()}>장소 직접 등록하기</ButtonLink>
        </EmptyState>
      ) : (
        <>
          <p className="px-5 pb-1 text-[13px] text-ink-sub">{data.length}곳 · 내가 만든 장소만 수정하거나 지울 수 있어요</p>
          <ul className="divide-y divide-line px-5">
            {data.map((p) => (
              <li key={p.id}>
                <PlaceListItem
                  place={p}
                  meta={`${visibilityLabel(p.visibility)}${p.photoCount ? ` · 내 사진 ${p.photoCount}장` : ""}`}
                  trailing={
                    <Link
                      href={routes.editPlace(p.id)}
                      className="inline-flex h-9 shrink-0 items-center gap-1 rounded-control border border-line px-3 text-[13px] font-medium"
                    >
                      <Pencil size={14} /> 수정
                    </Link>
                  }
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
