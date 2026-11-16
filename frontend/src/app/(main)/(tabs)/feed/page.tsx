import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { FeedScreen } from "./_components/FeedScreen";

export const metadata: Metadata = { title: "홈" };

/** 목록 · 지도 전환을 주소(searchParams)에서 읽으므로 Suspense 안에서 그립니다. */
export default function FeedPage() {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      <FeedScreen />
    </Suspense>
  );
}
