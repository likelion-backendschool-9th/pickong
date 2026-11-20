import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { EditPlaceScreen } from "./EditPlaceScreen";

export default function Page({ params }: PageProps<"/places/[placeId]/edit">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ placeId }) => (
        <EditPlaceScreen placeId={placeId} />
      ))}
    </Suspense>
  );
}
