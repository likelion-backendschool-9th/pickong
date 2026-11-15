"use client";

import { Pencil, Share2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCollection } from "@/entities/collection";
import { PlaceListItem } from "@/entities/place";
import { SavePlaceButton } from "@/features/save-place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { MapView } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Photo, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";

/** 컬렉션 상세 (공유 링크). 다른 사람의 공개 컬렉션에서도 장소를 내 지도에 저장할 수 있습니다. */
export function CollectionDetailScreen({ id }: { id: string }) {
  const { data: c, isPending, isError, error } = useCollection(id);
  const [mapOpen, setMapOpen] = useState(false);
  const toast = useToast();
  const router = useRouter();

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;

  const share = async () => {
    const url = window.location.href;
    try {
      if (navigator.share) await navigator.share({ title: c.title, url });
      else {
        await navigator.clipboard.writeText(url);
        toast({ message: "컬렉션 링크를 복사했어요" });
      }
    } catch {
      /* 취소 */
    }
  };

  return (
    <Screen>
      <TopBar
        title={c.isMine ? "내 컬렉션" : "공개 컬렉션"}
        backHref={routes.collections()}
        right={
          c.isMine && (
            <Link href={routes.editCollection(c.id)} aria-label="컬렉션 편집" className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
              <Pencil size={19} strokeWidth={1.6} />
            </Link>
          )
        }
      />
      {c.coverImageUrl && (
        <div className="relative aspect-[2/1] w-full bg-fill">
          <Photo src={c.coverImageUrl} alt="" priority />
        </div>
      )}
      <div className="px-5 pt-4">
        <h2 className="text-[22px] font-bold">{c.title}</h2>
        <p className="mt-1 text-[12px] text-ink-sub">
          <Link href={routes.user(c.owner.username)} className="font-bold text-ink">{c.owner.nickname}</Link> · {c.placeCount}곳 · {visibilityLabel(c.visibility)}
        </p>
        {c.description && <p className="mt-3 text-[14px] leading-relaxed text-ink-sub">{c.description}</p>}

        <button
          type="button"
          onClick={() => setMapOpen((v) => !v)}
          className="mt-4 flex w-full items-center justify-between rounded-card bg-soft px-4 py-3.5 text-left text-[13px] font-bold text-primary-deep"
          aria-expanded={mapOpen}
        >
          장소 {c.places.length}곳 {mapOpen ? "지도 접기" : "지도 보기 →"}
        </button>
        {mapOpen && (
          <MapView
            className="-mx-5 mt-3 h-64"
            markers={c.places.map((p, i) => ({ id: p.id, position: p.location, label: String(i + 1), title: p.name }))}
          />
        )}

        {c.places.length === 0 ? (
          <EmptyState title="아직 담긴 장소가 없어요" description="장소를 찜콩할 때 이 컬렉션을 선택해 보세요." />
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {c.places.map((p) => (
              <li key={p.id}>
                <PlaceListItem
                  place={p}
                  onClick={() => router.push(routes.place(p.id))}
                  meta={p.savedByMe ? "내 지도에 저장됨" : undefined}
                  trailing={
                    <SavePlaceButton place={p} source={{ type: "COLLECTION", refId: c.id, label: `${c.owner.nickname}님의 ‘${c.title}’에서 발견` }} />
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </div>
      <BottomCta>
        <Button block variant={c.isMine ? "primary" : "secondary"} onClick={share} icon={<Share2 size={17} />} disabled={c.visibility === "PRIVATE"}>
          {c.visibility === "PRIVATE" ? "비공개 컬렉션은 공유할 수 없어요" : "컬렉션 공유하기"}
        </Button>
      </BottomCta>
    </Screen>
  );
}
