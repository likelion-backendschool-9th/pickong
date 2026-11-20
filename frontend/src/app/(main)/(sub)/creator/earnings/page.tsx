import type { Metadata } from "next";
import { EarningsScreen } from "./EarningsScreen";

export const metadata: Metadata = { title: "수익 내역" };

export default function EarningsPage() {
  return <EarningsScreen />;
}
