import type { Metadata } from "next";
import { CreatorScreen } from "./CreatorScreen";

export const metadata: Metadata = { title: "크리에이터" };

export default function CreatorPage() {
  return <CreatorScreen />;
}
