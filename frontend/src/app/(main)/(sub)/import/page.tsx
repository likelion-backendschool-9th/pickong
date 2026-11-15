import type { Metadata } from "next";
import { ImportFlow } from "./_components/ImportFlow";

export const metadata: Metadata = { title: "AI 링크로 가져오기" };

export default function ImportPage() {
  return <ImportFlow />;
}
