import type { Metadata } from "next";
import { GroupChatsScreen } from "./GroupChatsScreen";

export const metadata: Metadata = { title: "그룹 채팅" };

export default function GroupChatsPage() {
  return <GroupChatsScreen />;
}
