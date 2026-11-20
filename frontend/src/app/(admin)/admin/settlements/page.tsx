import type { Metadata } from "next";
import { SettlementsScreen } from "./SettlementsScreen";

export const metadata: Metadata = { title: "정산 처리 · 관리자" };

export default function Page() {
  return <SettlementsScreen />;
}
