import Link from "next/link";
import type { Place } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { Avatar, Thumb } from "@/shared/ui/display";
import type { Post } from "../model";

/**
 * 게시물 한 줄: 장소가 연결된 사진 · 작성자 · 장소 이름 · 본문 첫 줄.
 * places를 주면 그 장소가 찍힌 사진과 이름을 보여 줍니다 (지도에 보이는 장소 등). 없으면 게시물의 모든 장소.
 */
export function PostPlaceRow({ post, places = post.places, showAuthor = true }: { post: Post; places?: Place[]; showAuthor?: boolean }) {
  const photo = post.photos.find((ph) => ph.placeId && places.some((p) => p.id === ph.placeId)) ?? post.photos[0];
  return (
    <Link href={routes.post(post.id)} className="flex gap-3 px-4 py-3 active:bg-fill">
      <Thumb src={photo.url} alt="" className="size-[72px] rounded-card" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          {showAuthor && (
            <>
              <Avatar src={post.author.profileImageUrl} name={post.author.nickname} size={20} />
              <span className="truncate text-[13px] font-bold">{post.author.nickname}</span>
              <span className="shrink-0 text-[12px] text-ink-faint">·</span>
            </>
          )}
          <span className="shrink-0 text-[12px] text-ink-faint">{formatRelative(post.createdAt)}</span>
        </div>
        <p className="mt-1 truncate text-[12px] font-bold text-primary-deep">{places.map((p) => p.name).join(" · ")}</p>
        <p className="mt-0.5 truncate text-[13px] text-ink-sub">{post.content.split("\n")[0]}</p>
      </div>
    </Link>
  );
}
