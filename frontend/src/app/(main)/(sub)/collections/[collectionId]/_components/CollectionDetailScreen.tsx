"use client";

import { Pencil, Share2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCollection } from "@/entities/collection";
import { PlaceListItem } from "@/entities/place";
import { PurchaseSheet } from "@/features/purchase-collection";
import { SavePlaceButton } from "@/features/save-place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { formatWon } from "@/shared/lib/money";
import { MapView } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { Badge, EmptyState, ErrorState, Photo, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { ClosedLock } from "@/shared/ui/VisibilityIcon";
import { useToast } from "@/shared/ui/Toast";

/**
 * 컬렉션 상세 (공유 링크). 다른 사람의 공개 컬렉션에서도 장소를 내 지도에 저장할 수 있습니다.
 * 유료 컬렉션을 아직 사지 않았으면 미리보기 장소만 보이고, 나머지는 잠겨 있어 구매 후 열람합니다.
 */
export function CollectionDetailScreen({ id }: { id: string }) {
  const { data: c, isPending, isError, error } = useCollection(id);
  const [mapOpen, setMapOpen] = useState(false);
  const [buying, setBuying] = useState(false);
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
        title={c.isMine ? "내 컬렉션" : c.price !== null ? "유료 컬렉션" : "공개 컬렉션"}
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
        <div className="flex items-center gap-2">
          <h2 className="min-w-0 truncate text-[22px] font-bold">{c.title}</h2>
          {c.price !== null && (c.isPurchased ? <Badge tone="neutral">구매함</Badge> : <Badge>{formatWon(c.price)}</Badge>)}
        </div>
        <p className="mt-1 text-[12px] text-ink-sub">
          <Link href={routes.user(c.owner.username)} className="font-bold text-ink">{c.owner.nickname}</Link> · {c.placeCount}곳 ·{" "}
          {c.price !== null ? (c.isMine ? `${formatWon(c.price)}에 판매 중` : "유료 컬렉션") : visibilityLabel(c.visibility)}
        </p>
        {c.description && <p className="mt-3 text-[14px] leading-relaxed text-ink-sub">{c.description}</p>}

        <button
          type="button"
          onClick={() => setMapOpen((v) => !v)}
          className="mt-4 flex w-full items-center justify-between rounded-card bg-soft px-4 py-3.5 text-left text-[13px] font-bold text-primary-deep"
          aria-expanded={mapOpen}
        >
          {c.isLocked ? `미리보기 ${c.places.length}곳` : `장소 ${c.places.length}곳`} {mapOpen ? "지도 접기" : "지도 보기 →"}
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

        {c.isLocked && c.lockedPlaceCount > 0 && (
          <div className="mt-2 rounded-card border border-dashed border-line px-4 py-5 text-center">
            <span className="mx-auto flex size-10 items-center justify-center rounded-full bg-fill text-ink-sub">
              <ClosedLock size={18} strokeWidth={2.25} />
            </span>
            <p className="mt-2 text-[15px] font-bold">{c.lockedPlaceCount}곳이 더 있어요</p>
            <p className="mt-1 text-[12px] text-ink-sub">
              {c.owner.nickname}님이 고른 나머지 장소는 구매 후 볼 수 있어요. 한 번 구매하면 계속 볼 수 있어요.
            </p>
          </div>
        )}
      </div>
      <BottomCta className={c.isLocked ? "grid grid-cols-[auto_1fr] gap-2" : undefined}>
        {c.isLocked ? (
          <>
            <Button variant="secondary" onClick={share} icon={<Share2 size={17} />} aria-label="컬렉션 공유하기" />
            <Button block onClick={() => setBuying(true)} icon={<ClosedLock size={17} strokeWidth={2.25} />}>
              {formatWon(c.price ?? 0)}에 전체 보기
            </Button>
          </>
        ) : (
          <Button block variant={c.isMine ? "primary" : "secondary"} onClick={share} icon={<Share2 size={17} />} disabled={c.visibility === "PRIVATE"}>
            {c.visibility === "PRIVATE" ? "비공개 컬렉션은 공유할 수 없어요" : "컬렉션 공유하기"}
          </Button>
        )}
      </BottomCta>
      {c.isLocked && <PurchaseSheet open={buying} onClose={() => setBuying(false)} collection={c} />}
    </Screen>
  );
}
