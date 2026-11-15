import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { EditCollectionScreen } from "./EditCollectionScreen";

export default function EditCollectionPage({ params }: PageProps<"/collections/[collectionId]/edit">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ collectionId }) => (
        <EditCollectionScreen id={collectionId} />
      ))}
    </Suspense>
  );
}
