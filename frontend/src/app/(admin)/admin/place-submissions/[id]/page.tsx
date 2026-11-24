import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlaceSubmissionDetailScreen } from "./PlaceSubmissionDetailScreen";

export const metadata: Metadata = { title: "제보 상세 · 관리자" };

export default function Page({ params }: PageProps<"/admin/place-submissions/[id]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ id }) => (
        <PlaceSubmissionDetailScreen id={id} />
      ))}
    </Suspense>
  );
}
