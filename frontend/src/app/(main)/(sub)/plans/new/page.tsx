import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { NewPlanFlow } from "./_components/NewPlanFlow";

export const metadata: Metadata = { title: "새 일정" };

export default function NewPlanPage() {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      <NewPlanFlow />
    </Suspense>
  );
}
