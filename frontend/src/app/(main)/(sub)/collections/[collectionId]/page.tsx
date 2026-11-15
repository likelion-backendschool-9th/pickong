import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { CollectionDetailScreen } from "./_components/CollectionDetailScreen";

export default function CollectionPage({ params }: PageProps<"/collections/[collectionId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ collectionId }) => (
        <CollectionDetailScreen id={collectionId} />
      ))}
    </Suspense>
  );
}
