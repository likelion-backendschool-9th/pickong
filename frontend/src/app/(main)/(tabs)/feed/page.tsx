import type { Metadata } from "next";
import { FeedScreen } from "./_components/FeedScreen";

export const metadata: Metadata = { title: "홈" };

export default function FeedPage() {
  return <FeedScreen />;
}
