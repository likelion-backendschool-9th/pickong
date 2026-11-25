"use client";

import { Camera, ChevronRight, Clock, Hash, MapPin, Pencil, Quote } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { categoryLabel, formatHours, usePlaceDetail } from "@/entities/place";
import { SavePlaceSheet } from "@/features/save-place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/date";
import { MapView } from "@/shared/map";
import { ButtonLink } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { Badge, ErrorState, Photo, SectionTitle, Skeleton, Thumb } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { usePlacePhotos, usePlaceTags } from "./_api/myRecordApi";

export function PlaceDetailScreen({ id }: { id: string }) {
  const { data, isPending, isError, error } = usePlaceDetail(id);
  const { data: collections = [] } = useMyCollections();
  const [saveOpen, setSaveOpen] = useState(false);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  const { place, saved, discoveredFrom, visits } = data;
  const inCollections = saved?.collectionIds.map((cid) => collections.find((c) => c.id === cid)?.title).filter(Boolean) ?? [];

  return (
    <Screen>
      <TopBar
        title="장소 상세"
        backHref={routes.map()}
        right={
          data.isOwner && (
            <Link href={routes.editPlace(place.id)} aria-label="장소 수정" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
              <Pencil size={19} strokeWidth={1.8} />
            </Link>
          )
        }
      />
      {place.imageUrl && (
        <div className="relative aspect-[16/10] w-full bg-fill">
          <Photo src={place.imageUrl} alt={place.name} priority />
        </div>
      )}
      <div className="px-5 pt-4 pb-6">
        <div className="flex items-center gap-2">
          <h2 className="text-[22px] font-bold">{place.name}</h2>
          {place.isCustom && <Badge tone="neutral">직접 지정</Badge>}
        </div>
        <p className="mt-1 text-[13px] text-ink-sub">
          {place.address} · {categoryLabel(place.category)}
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <Chip selected={!!saved} onClick={() => setSaveOpen(true)} icon={<MapPin size={14} />}>
            {saved ? "찜콩됨" : "찜콩하기"}
          </Chip>
          {saved && <Chip onClick={() => setSaveOpen(true)}>컬렉션 변경</Chip>}
          {saved?.visitStatus === "VISITED" && <Badge tone="neutral" className="h-8 px-2.5">방문 완료</Badge>}
        </div>
        {saved && (
          <p className="mt-2 text-[12px] text-ink-faint">
            {inCollections.length ? `컬렉션: ${inCollections.join(", ")}` : "컬렉션 없이 내 지도에 저장됨"}
          </p>
        )}

        {saved && <MyRecord placeId={place.id} />}

        <MapView className="-mx-5 mt-5 h-48" markers={[{ id: place.id, position: place.location, title: place.name }]} selectedId={place.id} />
        <div className="mt-3 flex items-center gap-2 text-[13px] text-ink-sub">
          <Clock size={15} /> 영업시간 {formatHours(place.businessHours)}
          {place.businessHours?.closedDays.length ? ` · 휴무 ${place.businessHours.closedDays.map((d) => "일월화수목금토"[d]).join(", ")}` : ""}
        </div>

        {discoveredFrom && (
          <Link href={routes.post(discoveredFrom.postId)} className="mt-6 flex gap-3 rounded-card bg-soft p-4">
            <Thumb src={discoveredFrom.imageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-bold">{saved?.source.label ?? `${discoveredFrom.authorUsername}님의 게시물에서 발견했어요`}</p>
              <p className="mt-1 flex gap-1 text-[13px] text-ink-sub">
                <Quote size={13} className="mt-0.5 shrink-0 text-primary-deep" />
                <span className="line-clamp-2">{discoveredFrom.quote}</span>
              </p>
              <p className="mt-1 text-[12px] text-ink-faint">관련 게시물 {data.relatedPostCount}개</p>
            </div>
          </Link>
        )}

        <SectionTitle className="mt-6 mb-2">방문 기록</SectionTitle>
        {visits.length === 0 ? (
          <p className="text-[13px] text-ink-faint">아직 방문 기록이 없어요. 일정에 넣고 다녀온 뒤 기록해 보세요.</p>
        ) : (
          <ul className="space-y-1">
            {visits.map((v) => (
              <li key={v.planId}>
                <Link href={routes.plan(v.planId)} className="text-[13px]">
                  <span className="font-bold">{formatShortDate(v.date)}</span> <span className="text-ink-sub">{v.title}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
      <BottomCta>
        <ButtonLink href={routes.newPlan([place.id])} block>
          일정에 추가
        </ButtonLink>
      </BottomCta>
      <SavePlaceSheet open={saveOpen} onClose={() => setSaveOpen(false)} places={[place]} source={{ type: "SEARCH", refId: null, label: "장소 상세에서 저장" }} />
    </Screen>
  );
}

/** 저장한 장소에 남긴 나만의 기록: 사진 · 태그 */
function MyRecord({ placeId }: { placeId: string }) {
  const { data: photos = [] } = usePlacePhotos(placeId);
  const { data: tags = [] } = usePlaceTags(placeId);
  return (
    <div className="mt-4 divide-y divide-line rounded-card border border-line">
      <Link href={routes.placePhotos(placeId)} className="flex items-center gap-3 px-4 py-3">
        <Camera size={17} className="shrink-0 text-primary-deep" aria-hidden />
        <span className="min-w-0 flex-1 text-[14px] font-bold">내 사진</span>
        <span className="text-[13px] text-ink-sub">{photos.length ? `${photos.length}장` : "추가하기"}</span>
        <ChevronRight size={16} className="text-ink-faint" />
      </Link>
      <Link href={routes.placeTags(placeId)} className="flex items-center gap-3 px-4 py-3">
        <Hash size={17} className="shrink-0 text-primary-deep" aria-hidden />
        <span className="shrink-0 text-[14px] font-bold">내 태그</span>
        <span className="min-w-0 flex-1 truncate text-right text-[13px] text-ink-sub">{tags.length ? tags.map((t) => `#${t}`).join(" ") : "추가하기"}</span>
        <ChevronRight size={16} className="text-ink-faint" />
      </Link>
    </div>
  );
}
