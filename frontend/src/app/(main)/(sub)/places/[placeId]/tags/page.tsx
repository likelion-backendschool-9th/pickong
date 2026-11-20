import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlaceTagsScreen } from "./PlaceTagsScreen";

export default function Page({ params }: PageProps<"/places/[placeId]/tags">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ placeId }) => (
        <PlaceTagsScreen placeId={placeId} />
      ))}
    </Suspense>
  );
}
