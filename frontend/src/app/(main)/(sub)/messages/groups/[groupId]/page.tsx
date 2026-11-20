import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { GroupChatSettingsScreen } from "./GroupChatSettingsScreen";

export default function GroupChatSettingsPage({ params }: PageProps<"/messages/groups/[groupId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ groupId }) => (
        <GroupChatSettingsScreen id={groupId} />
      ))}
    </Suspense>
  );
}
