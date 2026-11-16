import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { ChatScreen } from "./ChatScreen";

export default function ConversationPage({ params }: PageProps<"/messages/[conversationId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ conversationId }) => (
        <ChatScreen id={conversationId} />
      ))}
    </Suspense>
  );
}
