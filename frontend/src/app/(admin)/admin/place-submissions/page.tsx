import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlaceSubmissionsScreen } from "./PlaceSubmissionsScreen";

export const metadata: Metadata = { title: "제보 관리 · 관리자" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton className="m-5 h-48" />}>
      <PlaceSubmissionsScreen />
    </Suspense>
  );
}
