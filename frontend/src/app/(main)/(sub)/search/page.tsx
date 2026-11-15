import type { Metadata } from "next";
import { SearchScreen } from "./SearchScreen";

export const metadata: Metadata = { title: "검색" };

export default function SearchPage() {
  return <SearchScreen />;
}
