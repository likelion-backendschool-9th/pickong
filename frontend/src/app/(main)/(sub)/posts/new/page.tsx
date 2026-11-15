import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { ComposeFlow } from "./_components/ComposeFlow";

export const metadata: Metadata = { title: "게시물 올리기" };

export default function NewPostPage() {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      <ComposeFlow />
    </Suspense>
  );
}
