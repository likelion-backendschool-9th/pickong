"use client";

import { ImagePlus } from "lucide-react";
import { useRef } from "react";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { Photo } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { DraftPhoto } from "../_lib/draft";

/** 기기 갤러리 대신 보여주는 예시 사진. 실제 앱에서는 기기 사진 선택으로 대체됩니다. */
const SAMPLE_GALLERY = [
  "/images/photos/cafe.jpg",
  "/images/photos/shop-people.jpg",
  "/images/photos/shop.jpg",
  "/images/photos/park.jpg",
  "/images/photos/street.jpg",
];
const MAX = 10;

export function SelectStep({
  photos,
  onChange,
  onNext,
}: {
  photos: DraftPhoto[];
  onChange: (photos: DraftPhoto[]) => void;
  onNext: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const seq = useRef(0);
  const preview = photos.at(-1)?.url ?? SAMPLE_GALLERY[0];
  const orderOf = (url: string) => photos.findIndex((p) => p.url === url);

  const toggle = (url: string) => {
    const i = orderOf(url);
    if (i >= 0) onChange(photos.filter((_, k) => k !== i));
    else if (photos.length < MAX) onChange([...photos, { key: `${url}-${++seq.current}`, url, place: null }]);
  };

  const addFiles = (files: FileList | null) => {
    if (!files) return;
    const added = [...files].slice(0, MAX - photos.length).map((f, i) => ({
      key: `file-${++seq.current}-${i}`,
      url: URL.createObjectURL(f),
      place: null,
    }));
    onChange([...photos, ...added]);
  };

  const deviceOnly = photos.filter((p) => !SAMPLE_GALLERY.includes(p.url));

  return (
    <Screen>
      <TopBar title="사진 선택" backHref={routes.feed()} />
      <div className="relative aspect-[13/10] w-full bg-fill">
        <Photo src={preview} alt="선택한 사진 미리보기" priority />
      </div>
      <div className="flex items-center justify-between px-4 py-3">
        <span className="text-[14px] font-bold">최근 항목</span>
        <span className="text-[12px] text-ink-sub">여러 장 선택 · 최대 {MAX}장</span>
      </div>
      <div className="grid grid-cols-3 gap-0.5">
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          className="flex aspect-square flex-col items-center justify-center gap-1 bg-soft text-[12px] font-bold text-primary-deep"
        >
          <ImagePlus size={22} /> 기기에서 선택
        </button>
        <input ref={fileRef} type="file" accept="image/*" multiple className="hidden" onChange={(e) => addFiles(e.target.files)} />
        {[...deviceOnly.map((p) => p.url), ...SAMPLE_GALLERY].map((url) => {
          const order = orderOf(url);
          return (
            <button
              key={url}
              type="button"
              onClick={() => toggle(url)}
              aria-pressed={order >= 0}
              aria-label={order >= 0 ? `선택됨 ${order + 1}번` : "사진 선택"}
              className="relative aspect-square bg-fill"
            >
              <Photo src={url} alt="" sizes="160px" />
              <span
                className={cn(
                  "absolute top-1.5 right-1.5 flex size-6 items-center justify-center rounded-full border-2 text-[12px] font-bold",
                  order >= 0 ? "border-white bg-primary text-on-primary" : "border-white/90 bg-black/20",
                )}
              >
                {order >= 0 ? order + 1 : ""}
              </span>
              {order >= 0 && <span className="absolute inset-0 ring-2 ring-primary ring-inset" />}
            </button>
          );
        })}
      </div>
      <p className="px-4 pt-3 text-[12px] text-ink-faint">선택한 순서대로 게시돼요. 다음 단계에서 순서를 바꿀 수 있어요.</p>
      <BottomCta>
        <Button block disabled={photos.length === 0} onClick={onNext}>
          다음 · 장소 연결 {photos.length > 0 && `(${photos.length}장)`}
        </Button>
      </BottomCta>
    </Screen>
  );
}
