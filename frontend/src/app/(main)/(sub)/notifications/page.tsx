import type { Metadata } from "next";
import { NotificationsScreen } from "./NotificationsScreen";

export const metadata: Metadata = { title: "알림" };

export default function NotificationsPage() {
  return <NotificationsScreen />;
}
