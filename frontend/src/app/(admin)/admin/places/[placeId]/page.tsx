import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { AdminPlaceDetailScreen } from "../_components/AdminPlaceDetailScreen";

export const metadata: Metadata = { title: "장소 상세 · 관리자" };

export default function Page({ params }: PageProps<"/admin/places/[placeId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ placeId }) => (
        <AdminPlaceDetailScreen placeId={placeId} />
      ))}
    </Suspense>
  );
}
