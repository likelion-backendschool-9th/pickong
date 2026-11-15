"use client";

import { useState } from "react";
import { CollectionPicker } from "@/features/save-place";
import { errorMessage } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Notice, SectionTitle } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { saveTargets } from "../_lib/candidateSelection";

export function CollectionStep({
  targets,
  saving,
  error,
  onBack,
  onSave,
}: {
  targets: ReturnType<typeof saveTargets>;
  saving: boolean;
  error: unknown;
  onBack: () => void;
  onSave: (collectionIds: string[]) => void;
}) {
  const [collectionIds, setCollectionIds] = useState<string[]>([]);
  const total = targets.placeIds.length;
  return (
    <Screen>
      <TopBar title="저장할 컬렉션" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[22px] font-bold">선택한 장소 {total}곳</h2>
        <p className="mt-1.5 text-[13px] text-ink-sub">{targets.candidates.map((c) => c.matchedPlace!.name).join(" · ")}</p>
        <Notice className="mt-4" icon={false}>
          새 장소 {targets.newCount}곳 저장{targets.existingCount ? ` + 기존 장소 ${targets.existingCount}곳 컬렉션 연결` : ""}
        </Notice>
        <SectionTitle className="mt-6 mb-2">컬렉션 선택</SectionTitle>
        <CollectionPicker selected={collectionIds} onChange={setCollectionIds} />
        <p className="mt-3 text-[12px] text-ink-faint">저장 후 원본 링크를 함께 남겨요.</p>
        {!!error && <Notice tone="danger" className="mt-3">{errorMessage(error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={saving} onClick={() => onSave(collectionIds)}>
          {total}곳 저장{collectionIds.length ? " 및 연결" : ""}
        </Button>
      </BottomCta>
    </Screen>
  );
}
