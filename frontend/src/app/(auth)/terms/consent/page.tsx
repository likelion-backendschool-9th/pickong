import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { ConsentScreen } from "./ConsentScreen";

export const metadata: Metadata = { title: "바뀐 약관 확인" };

/**
 * 바뀐 약관 재동의 (로그인 필요). /terms/[type]보다 고정 경로가 먼저라 'consent'는 약관 종류로 해석되지 않습니다.
 * 동의한 뒤 돌아갈 곳은 ?next=로 받습니다.
 */
export default function TermsConsentPage({ searchParams }: PageProps<"/terms/consent">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {searchParams.then(({ next }) => (
        <ConsentScreen next={typeof next === "string" ? next : undefined} />
      ))}
    </Suspense>
  );
}
