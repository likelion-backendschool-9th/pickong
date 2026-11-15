import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { Thumb } from "@/shared/ui/display";
import type { Collection } from "../model";

/** 컬렉션 목록 한 줄: 대표 이미지 · 이름 · 장소 수 · 공개 범위 */
export function CollectionCard({ collection, href }: { collection: Collection; href?: string }) {
  return (
    <Link href={href ?? routes.collection(collection.id)} className="flex items-center gap-4 py-3 active:opacity-80">
      <Thumb src={collection.coverImageUrl} alt="" className="size-[72px] rounded-card" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[15px] font-bold">{collection.title}</div>
        <div className="mt-1 text-[12px] text-ink-sub">
          {collection.placeCount}곳 · {visibilityLabel(collection.visibility)}
        </div>
        <div className="mt-1.5 text-[12px] font-bold text-primary-deep">지도 보기 →</div>
      </div>
    </Link>
  );
}
