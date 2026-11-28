import type { Metadata } from "next";
import { AdminPlaceCreateScreen } from "../_components/AdminPlaceCreateScreen";

export const metadata: Metadata = { title: "장소 추가 · 관리자" };

export default function Page() {
  return <AdminPlaceCreateScreen />;
}
