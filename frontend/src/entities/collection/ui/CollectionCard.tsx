import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { formatWon } from "@/shared/lib/money";
import { Badge, Thumb } from "@/shared/ui/display";
import { ClosedLock } from "@/shared/ui/VisibilityIcon";
import type { Collection } from "../model";

/** 컬렉션 목록 한 줄: 대표 이미지 · 이름 · 장소 수 · 공개 범위 (유료면 가격 · 잠금 · 구매함 표시) */
export function CollectionCard({ collection, href }: { collection: Collection; href?: string }) {
  const c = collection;
  return (
    <Link href={href ?? routes.collection(c.id)} className="flex items-center gap-4 py-3 active:opacity-80">
      <div className="relative shrink-0">
        <Thumb src={c.coverImageUrl} alt="" className="size-[72px] rounded-card" />
        {c.isLocked && (
          <span className="absolute inset-0 flex items-center justify-center rounded-card bg-black/35 text-white" aria-hidden>
            <ClosedLock size={20} strokeWidth={2.25} />
          </span>
        )}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-bold">{c.title}</span>
          {c.price !== null && (c.isPurchased ? <Badge tone="neutral">구매함</Badge> : <Badge>{formatWon(c.price)}</Badge>)}
        </div>
        <div className="mt-1 text-[12px] text-ink-sub">
          {c.placeCount}곳 · {c.price !== null ? "유료 컬렉션" : visibilityLabel(c.visibility)}
        </div>
        <div className="mt-1.5 text-[12px] font-bold text-primary-deep">{c.isLocked ? "미리보기 →" : "지도 보기 →"}</div>
      </div>
    </Link>
  );
}
