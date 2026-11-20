import type { Metadata } from "next";
import { PayoutAccountScreen } from "./PayoutAccountScreen";

export const metadata: Metadata = { title: "정산 계좌" };

export default function PayoutAccountPage() {
  return <PayoutAccountScreen />;
}
