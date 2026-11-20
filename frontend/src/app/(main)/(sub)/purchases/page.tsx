import type { Metadata } from "next";
import { PurchasesScreen } from "./PurchasesScreen";

export const metadata: Metadata = { title: "구매 내역" };

export default function PurchasesPage() {
  return <PurchasesScreen />;
}
