import Link from "next/link";
import { Layers } from "lucide-react";
import { routes } from "@/shared/config/routes";
import { Photo } from "@/shared/ui/display";
import type { Post } from "../model";

/** 프로필 게시물 탭: 사진 그리드 */
export function PostGrid({ posts }: { posts: Post[] }) {
  return (
    <div className="grid grid-cols-3 gap-0.5">
      {posts.map((post) => (
        <Link key={post.id} href={routes.post(post.id)} className="relative aspect-square bg-fill" aria-label={post.content.split("\n")[0]}>
          <Photo src={post.photos[0].url} alt="" sizes="160px" />
          {post.photos.length > 1 && <Layers size={16} className="absolute top-1.5 right-1.5 text-white drop-shadow" />}
        </Link>
      ))}
    </div>
  );
}
