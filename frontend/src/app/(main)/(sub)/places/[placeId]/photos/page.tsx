import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { PlacePhotosScreen } from "./PlacePhotosScreen";

export default function Page({ params }: PageProps<"/places/[placeId]/photos">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ placeId }) => (
        <PlacePhotosScreen placeId={placeId} />
      ))}
    </Suspense>
  );
}
