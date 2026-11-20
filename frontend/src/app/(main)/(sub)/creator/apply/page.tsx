import type { Metadata } from "next";
import { CreatorApplyScreen } from "./CreatorApplyScreen";

export const metadata: Metadata = { title: "크리에이터 신청" };

export default function CreatorApplyPage() {
  return <CreatorApplyScreen />;
}
