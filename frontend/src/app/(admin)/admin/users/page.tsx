import type { Metadata } from "next";
import { Suspense } from "react";
import { AdminUsersScreen } from "./AdminUsersScreen";

export const metadata: Metadata = { title: "사용자 제재 · 관리자" };

export default function Page() {
  return <Suspense fallback={<p className="p-5">사용자 목록을 불러오는 중이에요.</p>}><AdminUsersScreen /></Suspense>;
}
