import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { MyMapScreen } from "./_components/MyMapScreen";

export const metadata: Metadata = { title: "내 지도" };

export default function MapPage() {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      <MyMapScreen />
    </Suspense>
  );
}
