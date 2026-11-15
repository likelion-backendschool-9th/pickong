"use client";

import { useMe } from "@/entities/user";
import type { Post } from "@/entities/post";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { ButtonLink } from "@/shared/ui/Button";
import { Photo } from "@/shared/ui/display";
import { Screen, TopBar } from "@/shared/ui/layout";

export function DoneStep({ post }: { post: Post }) {
  const { data: me } = useMe();
  return (
    <Screen>
      <TopBar title="게시 완료" backHref={routes.feed()} onBack={() => window.location.assign(routes.feed())} />
      <div className="relative aspect-[13/10] w-full bg-fill">
        <Photo src={post.photos[0].url} alt="" priority />
      </div>
      <div className="px-5 pt-6">
        <h2 className="text-[20px] font-bold">오늘의 장소를 공유했어요</h2>
        <p className="mt-2 text-[13px] text-ink-sub">
          사진 {post.photos.length}장 · 연결한 장소 {post.places.length}곳 · {visibilityLabel(post.visibility)}
        </p>
        <p className="mt-1 text-[13px] text-ink-sub">피드에서 사진을 넘기면 장소도 함께 바뀌어요.</p>
        <div className="mt-8 flex flex-col gap-2">
          <ButtonLink href={routes.post(post.id)} replace block>
            게시물 보러 가기
          </ButtonLink>
          <ButtonLink href={me ? routes.user(me.username) : routes.feed()} replace variant="secondary" block>
            내 프로필
          </ButtonLink>
        </div>
      </div>
    </Screen>
  );
}
