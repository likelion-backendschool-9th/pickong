import type { Metadata } from "next";
import { AgreementsScreen } from "./AgreementsScreen";

export const metadata: Metadata = { title: "약관 및 동의 관리" };

export default function AgreementsPage() {
  return <AgreementsScreen />;
}
