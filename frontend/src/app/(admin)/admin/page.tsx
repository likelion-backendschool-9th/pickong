import type { Metadata } from "next";
import { AdminDashboard } from "./AdminDashboard";

export const metadata: Metadata = { title: "관리자" };

export default function AdminPage() {
  return <AdminDashboard />;
}
