"use client";

import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { visibilityLabel, type Visibility } from "@/shared/config/visibility";
import { Button } from "@/shared/ui/Button";
import { Notice, Photo } from "@/shared/ui/display";
import { Field, SelectTrigger, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { VisibilitySheet } from "@/shared/ui/VisibilitySheet";
import { summarizePhotoPlaces, uniqueDraftPlaces, type DraftPhoto } from "../_lib/draft";

export function WriteStep({
  photos,
  content,
  onContent,
  visibility,
  onVisibility,
  onBack,
  onPublish,
  publishing,
  error,
}: {
  photos: DraftPhoto[];
  content: string;
  onContent: (v: string) => void;
  visibility: Visibility;
  onVisibility: (v: Visibility) => void;
  onBack: () => void;
  onPublish: () => void;
  publishing: boolean;
  error: unknown;
}) {
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  return (
    <Screen>
      <TopBar title="게시물 작성" onBack={onBack} />
      <div className="scrollbar-none flex gap-1.5 overflow-x-auto px-5 pt-1">
        {photos.map((p, i) => (
          <div key={p.key} className="relative size-16 shrink-0 overflow-hidden rounded-lg bg-fill">
            <Photo src={p.url} alt={`사진 ${i + 1}`} sizes="64px" />
            <span className="absolute bottom-0.5 left-0.5 rounded bg-black/55 px-1 text-[10px] font-bold text-white">{i + 1}</span>
          </div>
        ))}
      </div>
      <div className="space-y-6 px-5 pt-5 pb-6">
        <div>
          <h3 className="text-[13px] font-bold">사진별 장소 확인</h3>
          <p className="mt-1 text-[13px] text-ink-sub">{summarizePhotoPlaces(photos)}</p>
          <button type="button" onClick={onBack} className="mt-2 text-[13px] font-bold text-primary-deep">
            사진 순서 / 장소 다시 편집 →
          </button>
        </div>
        <Field label="본문" htmlFor="post-content">
          <Textarea
            id="post-content"
            value={content}
            onChange={(e) => onContent(e.target.value)}
            placeholder={"오늘 다녀온 장소 이야기를 남겨 주세요.\n예: 주말엔 성수 한 바퀴, 커피부터 산책까지!"}
            className="min-h-36"
            maxLength={2000}
          />
        </Field>
        <Field
          label="공개 범위"
          htmlFor="post-vis"
          hint={
            visibility === "PUBLIC"
              ? `전체 공개 게시물의 장소 ${uniqueDraftPlaces(photos).length}곳은 피드 지도에 표시될 수 있어요.`
              : undefined
          }
        >
          <SelectTrigger id="post-vis" value={visibilityLabel(visibility)} onClick={() => setVisibilityOpen(true)} />
        </Field>
        {!!error && <Notice tone="danger">{errorMessage(error)}</Notice>}
      </div>
      <BottomCta>
        <Button block onClick={onPublish} loading={publishing}>
          게시하기
        </Button>
      </BottomCta>
      <VisibilitySheet open={visibilityOpen} value={visibility} onClose={() => setVisibilityOpen(false)} onChange={onVisibility} />
    </Screen>
  );
}
