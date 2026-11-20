import type { Metadata } from "next";
import { ContentsScreen } from "./ContentsScreen";

export const metadata: Metadata = { title: "콘텐츠 관리 · 관리자" };

export default function Page() {
  return <ContentsScreen />;
}
