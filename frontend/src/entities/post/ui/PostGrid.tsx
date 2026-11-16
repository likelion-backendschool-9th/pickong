import Link from "next/link";
import { Layers } from "lucide-react";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { Photo } from "@/shared/ui/display";
import { VisibilityIcon } from "@/shared/ui/VisibilityIcon";
import type { Post } from "../model";

/**
 * 프로필 게시물 탭: 사진 그리드.
 * showVisibility면 모든 게시물에 공개 범위 아이콘(전체 공개 · 팔로워 공개 · 비공개)을 표시합니다 (내 프로필용).
 */
export function PostGrid({ posts, showVisibility = false }: { posts: Post[]; showVisibility?: boolean }) {
  return (
    <div className="grid grid-cols-3 gap-0.5">
      {posts.map((post) => {
        const title = post.content.split("\n")[0];
        return (
          <Link
            key={post.id}
            href={routes.post(post.id)}
            className="relative aspect-square bg-fill"
            aria-label={showVisibility ? `${title} (${visibilityLabel(post.visibility)})` : title}
          >
            <Photo src={post.photos[0].url} alt="" sizes="160px" />
            {showVisibility && (
              <VisibilityIcon
                visibility={post.visibility}
                size={15}
                strokeWidth={2.25}
                aria-hidden
                className="absolute top-1.5 left-1.5 text-white drop-shadow"
              />
            )}
            {post.photos.length > 1 && <Layers size={16} className="absolute top-1.5 right-1.5 text-white drop-shadow" />}
          </Link>
        );
      })}
    </div>
  );
}
