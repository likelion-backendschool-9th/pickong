import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { CheckoutScreen } from "./CheckoutScreen";

export const metadata: Metadata = { title: "결제" };

export default function CheckoutPage({ params }: PageProps<"/checkout/[collectionId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ collectionId }) => (
        <CheckoutScreen collectionId={collectionId} />
      ))}
    </Suspense>
  );
}
