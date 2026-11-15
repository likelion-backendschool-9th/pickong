import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PostDetailScreen } from "./PostDetailScreen";

export default function PostPage({ params }: PageProps<"/posts/[postId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ postId }) => (
        <PostDetailScreen id={postId} />
      ))}
    </Suspense>
  );
}
