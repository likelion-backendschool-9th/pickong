import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlanEditor } from "./_components/PlanEditor";

export default function PlanPage({ params }: PageProps<"/plans/[planId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ planId }) => (
        <PlanEditor planId={planId} />
      ))}
    </Suspense>
  );
}
