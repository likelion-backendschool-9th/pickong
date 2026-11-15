import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlaceDetailScreen } from "./PlaceDetailScreen";

export default function PlacePage({ params }: PageProps<"/places/[placeId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ placeId }) => (
        <PlaceDetailScreen id={placeId} />
      ))}
    </Suspense>
  );
}
