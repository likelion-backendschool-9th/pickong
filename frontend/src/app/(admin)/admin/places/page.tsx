import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { AdminPlacesScreen } from "./_components/AdminPlacesScreen";

export const metadata: Metadata = { title: "지도 관리 · 관리자" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton className="m-5 h-48" />}>
      <AdminPlacesScreen />
    </Suspense>
  );
}
