"use client";

import { MapPinPlus, MessageCircle, MoreHorizontal, Send } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { PlaceInfoBar, type Place } from "@/entities/place";
import { placeOfPhoto, type Post } from "@/entities/post";
import { BookmarkButton } from "@/features/bookmark-post";
import { LikeButton } from "@/features/like-post";
import { SavePlaceSheet } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatRelative } from "@/shared/lib/date";
import { Carousel, Dots } from "@/shared/ui/Carousel";
import { Avatar, Photo } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";
import { CommentSheet } from "./CommentSheet";
import { PlaceMapSheet } from "./PlaceMapSheet";

type Sheet = { type: "map"; place: Place } | { type: "save"; single?: Place } | { type: "comments" } | null;

/**
 * 피드 카드. 사진을 넘기면 사진 아래 장소 정보도 함께 바뀝니다.
 * 사진 번호와 시트 상태를 카드가 가지고 있어서, 시트를 닫거나 저장한 뒤에도 보던 사진이 유지됩니다.
 */
export function PostCard({ post, priority }: { post: Post; priority?: boolean }) {
  const [index, setIndex] = useState(0);
  const [sheet, setSheet] = useState<Sheet>(null);
  const toast = useToast();
  const currentPlace = placeOfPhoto(post, index);
  const close = () => setSheet(null);

  const share = async () => {
    const url = `${window.location.origin}${routes.post(post.id)}`;
    try {
      if (navigator.share) await navigator.share({ title: "찜콩", url });
      else {
        await navigator.clipboard.writeText(url);
        toast({ message: "링크를 복사했어요" });
      }
    } catch {
      /* 공유 취소 */
    }
  };

  return (
    <article className="pb-5" aria-label={`${post.author.username}님의 게시물`}>
      <header className="flex items-center gap-2.5 px-4 py-2.5">
        <Link href={routes.user(post.author.username)} className="flex min-w-0 flex-1 items-center gap-2.5">
          <Avatar src={post.author.profileImageUrl} name={post.author.username} size={36} />
          <span className="min-w-0">
            <span className="block truncate text-[14px] font-bold">{post.author.username}</span>
            <span className="block truncate text-[12px] text-ink-faint">
              {post.author.region} · {formatRelative(post.createdAt)}
            </span>
          </span>
        </Link>
        <button type="button" aria-label="더 보기" className="inline-flex size-10 items-center justify-center rounded-full text-ink-sub active:bg-fill">
          <MoreHorizontal size={20} />
        </button>
      </header>

      <Carousel
        className="aspect-[13/10] w-full bg-fill"
        count={post.photos.length}
        index={index}
        onIndexChange={setIndex}
        label={`${post.author.username}님의 사진`}
        renderSlide={(i) => (
          <Photo src={post.photos[i].url} alt={placeOfPhoto(post, i)?.name ?? "게시물 사진"} priority={priority && i === 0} />
        )}
      />

      <PlaceInfoBar place={currentPlace} onClick={() => currentPlace && setSheet({ type: "map", place: currentPlace })} />
      <Dots count={post.photos.length} index={index} />

      <div className="mt-1 flex items-center px-1.5">
        <LikeButton post={post} />
        <button
          type="button"
          aria-label="댓글"
          onClick={() => setSheet({ type: "comments" })}
          className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill"
        >
          <MessageCircle size={23} strokeWidth={1.6} />
        </button>
        <button type="button" aria-label="공유" onClick={share} className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill">
          <Send size={22} strokeWidth={1.6} />
        </button>

        <div className="ml-auto flex items-center gap-1">
          <button
            type="button"
            onClick={() => setSheet({ type: "save" })}
            disabled={post.places.length === 0}
            aria-label="장소 찜콩"
            className={cn(
              "inline-flex h-11 min-w-11 flex-col items-center justify-center rounded-full px-1 active:bg-fill disabled:opacity-40",
            )}
          >
            <MapPinPlus size={22} strokeWidth={1.6} className="text-primary-deep" />
            <span className="text-[10px] text-ink-sub">장소 찜콩</span>
          </button>
          <BookmarkButton post={post} />
        </div>
      </div>

      <div className="px-4">
        <p className="text-[13px] font-bold">좋아요 {post.likeCount.toLocaleString()}개</p>
        <p className="mt-1 text-[14px] leading-relaxed whitespace-pre-line">
          <Link href={routes.user(post.author.username)} className="mr-1.5 font-bold">
            {post.author.username}
          </Link>
          {post.content}
        </p>
        {post.commentCount > 0 && (
          <button type="button" onClick={() => setSheet({ type: "comments" })} className="mt-1.5 text-[13px] text-ink-faint">
            댓글 {post.commentCount}개 보기
          </button>
        )}
      </div>

      <PlaceMapSheet
        place={sheet?.type === "map" ? sheet.place : null}
        onClose={close}
        onSave={(place) => setSheet({ type: "save", single: place })}
      />
      <SavePlaceSheet
        open={sheet?.type === "save"}
        onClose={close}
        places={sheet?.type === "save" && sheet.single ? [sheet.single] : post.places}
        currentPlace={sheet?.type === "save" && sheet.single ? undefined : currentPlace}
        source={{ type: "POST", refId: post.id, label: `${post.author.username}님의 게시물에서 발견` }}
      />
      <CommentSheet postId={post.id} open={sheet?.type === "comments"} onClose={close} />
    </article>
  );
}
