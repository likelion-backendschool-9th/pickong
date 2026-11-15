import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { ProfileScreen } from "./_components/ProfileScreen";

export default function UserPage({ params }: PageProps<"/users/[username]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ username }) => (
        <ProfileScreen username={username} />
      ))}
    </Suspense>
  );
}
