import type { Metadata } from "next";
import { NewGroupChatScreen } from "./NewGroupChatScreen";

export const metadata: Metadata = { title: "그룹 채팅 만들기" };

export default function NewGroupChatPage() {
  return <NewGroupChatScreen />;
}
