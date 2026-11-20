import type { Metadata } from "next";
import { MyPlacesScreen } from "./MyPlacesScreen";

export const metadata: Metadata = { title: "직접 등록한 장소" };

export default function MyPlacesPage() {
  return <MyPlacesScreen />;
}
