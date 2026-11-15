import type { Metadata } from "next";
import { NewPlaceScreen } from "./NewPlaceScreen";

export const metadata: Metadata = { title: "장소 직접 추가" };

export default function NewPlacePage() {
  return <NewPlaceScreen />;
}
