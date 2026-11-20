import { notFound } from "next/navigation";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { TermsScreen } from "./TermsScreen";

const TYPES = ["service", "privacy", "marketing"];

export default function TermsPage({ params }: PageProps<"/terms/[type]">) {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      {params.then(({ type }) => {
        if (!TYPES.includes(type)) notFound();
        return <TermsScreen type={type} />;
      })}
    </Suspense>
  );
}
