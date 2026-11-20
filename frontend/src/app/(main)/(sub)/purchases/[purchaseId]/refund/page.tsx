import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { RefundScreen } from "./RefundScreen";

export const metadata: Metadata = { title: "환불 신청" };

export default function RefundPage({ params }: PageProps<"/purchases/[purchaseId]/refund">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ purchaseId }) => (
        <RefundScreen purchaseId={purchaseId} />
      ))}
    </Suspense>
  );
}
