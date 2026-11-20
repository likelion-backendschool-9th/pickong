import type { Metadata } from "next";
import { DevicesScreen } from "./DevicesScreen";

export const metadata: Metadata = { title: "디바이스 관리" };

export default function DevicesPage() {
  return <DevicesScreen />;
}
