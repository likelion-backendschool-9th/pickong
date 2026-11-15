import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { VisitRecordScreen } from "./VisitRecordScreen";

export default function VisitsPage({ params }: PageProps<"/plans/[planId]/visits">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ planId }) => (
        <VisitRecordScreen planId={planId} />
      ))}
    </Suspense>
  );
}
