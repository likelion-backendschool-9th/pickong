import type { Metadata } from "next";
import { Suspense } from "react";
import { ContentsScreen } from "./ContentsScreen";

export const metadata: Metadata = { title: "콘텐츠 관리 · 관리자" };

export default function Page() {
  return <Suspense fallback={<p className="p-5">콘텐츠 목록을 불러오는 중이에요.</p>}><ContentsScreen /></Suspense>;
}
