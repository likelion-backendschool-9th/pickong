import type { Metadata } from "next";
import { PlansScreen } from "./PlansScreen";

export const metadata: Metadata = { title: "일정" };

export default function PlansPage() {
  return <PlansScreen />;
}
