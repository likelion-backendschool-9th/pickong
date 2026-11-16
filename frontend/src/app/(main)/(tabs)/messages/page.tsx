import type { Metadata } from "next";
import { MessagesScreen } from "./_components/MessagesScreen";

export const metadata: Metadata = { title: "메시지" };

export default function MessagesPage() {
  return <MessagesScreen />;
}
