import type { Metadata } from "next";
import { NotificationSettingsScreen } from "./NotificationSettingsScreen";

export const metadata: Metadata = { title: "푸시 알림 설정" };

export default function NotificationSettingsPage() {
  return <NotificationSettingsScreen />;
}
