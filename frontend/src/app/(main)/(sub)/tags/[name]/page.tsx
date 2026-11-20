import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { TagPlacesScreen } from "./TagPlacesScreen";

export default function TagPlacesPage({ params }: PageProps<"/tags/[name]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ name }) => (
        <TagPlacesScreen name={decodeURIComponent(name)} />
      ))}
    </Suspense>
  );
}
