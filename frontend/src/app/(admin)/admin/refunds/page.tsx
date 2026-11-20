import type { Metadata } from "next";
import { RefundsScreen } from "./RefundsScreen";

export const metadata: Metadata = { title: "환불 처리 · 관리자" };

export default function Page() {
  return <RefundsScreen />;
}
