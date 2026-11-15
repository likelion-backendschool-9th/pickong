import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { FollowsScreen } from "./FollowsScreen";

export default function FollowsPage({ params }: PageProps<"/users/[username]/follows">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ username }) => (
        <FollowsScreen username={username} />
      ))}
    </Suspense>
  );
}
