import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { TagsScreen } from "./TagsScreen";

export const metadata: Metadata = { title: "내 태그" };

export default function TagsPage({ searchParams }: PageProps<"/tags">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {searchParams.then((sp) => (
        <TagsScreen initialQuery={typeof sp.q === "string" ? sp.q : ""} />
      ))}
    </Suspense>
  );
}
