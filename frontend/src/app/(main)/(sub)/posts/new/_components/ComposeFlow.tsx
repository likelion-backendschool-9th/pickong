"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useCreatePost, type Post } from "@/entities/post";
import { usePlan } from "@/entities/plan";
import type { Visibility } from "@/shared/config/visibility";
import type { DraftPhoto } from "../_lib/draft";
import { DoneStep } from "./DoneStep";
import { LinkStep } from "./LinkStep";
import { SelectStep } from "./SelectStep";
import { WriteStep } from "./WriteStep";

type Step = "select" | "link" | "write" | "done";

/** 게시물 작성: 사진 선택 → 사진별 장소 연결 → 본문·공개 범위 → 게시 완료 */
export function ComposeFlow() {
  const params = useSearchParams();
  const fromPlan = params.get("fromPlan");
  const { data: plan } = usePlan(fromPlan ?? "");
  const visitedPlaces = fromPlan && plan ? plan.items.filter((i) => i.visited).map((i) => i.place) : [];

  const [step, setStep] = useState<Step>("select");
  const [photos, setPhotos] = useState<DraftPhoto[]>([]);
  const [content, setContent] = useState("");
  const [visibility, setVisibility] = useState<Visibility>("PUBLIC");
  const [created, setCreated] = useState<Post | null>(null);
  const create = useCreatePost();

  const publish = () =>
    create.mutate(
      { photos: photos.map((p) => ({ url: p.url, placeId: p.place?.id ?? null })), content, visibility },
      {
        onSuccess: (post) => {
          setCreated(post);
          setStep("done");
        },
      },
    );

  switch (step) {
    case "select":
      return <SelectStep photos={photos} onChange={setPhotos} onNext={() => setStep("link")} />;
    case "link":
      return (
        <LinkStep
          photos={photos}
          onChange={setPhotos}
          suggestedPlaces={visitedPlaces}
          onBack={() => setStep("select")}
          onNext={() => setStep("write")}
        />
      );
    case "write":
      return (
        <WriteStep
          photos={photos}
          content={content}
          onContent={setContent}
          visibility={visibility}
          onVisibility={setVisibility}
          onBack={() => setStep("link")}
          onPublish={publish}
          publishing={create.isPending}
          error={create.error}
        />
      );
    case "done":
      return created ? <DoneStep post={created} /> : null;
  }
}
