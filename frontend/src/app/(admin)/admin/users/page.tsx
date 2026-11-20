import type { Metadata } from "next";
import { AdminUsersScreen } from "./AdminUsersScreen";

export const metadata: Metadata = { title: "사용자 제재 · 관리자" };

export default function Page() {
  return <AdminUsersScreen />;
}
