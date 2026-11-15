import type { Metadata } from "next";
import { MyCollectionsScreen } from "./MyCollectionsScreen";

export const metadata: Metadata = { title: "내 컬렉션" };

export default function CollectionsPage() {
  return <MyCollectionsScreen />;
}
