import type { Metadata } from "next";
import { CreatorReviewScreen } from "./CreatorReviewScreen";

export const metadata: Metadata = { title: "크리에이터 심사 · 관리자" };

export default function Page() {
  return <CreatorReviewScreen />;
}
