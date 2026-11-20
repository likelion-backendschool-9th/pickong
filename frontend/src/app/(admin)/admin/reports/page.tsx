import type { Metadata } from "next";
import { ReportsScreen } from "./ReportsScreen";

export const metadata: Metadata = { title: "신고 처리 · 관리자" };

export default function Page() {
  return <ReportsScreen />;
}
