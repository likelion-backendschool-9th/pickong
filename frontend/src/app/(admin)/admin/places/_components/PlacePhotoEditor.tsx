"use client";

import { ImagePlus } from "lucide-react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Photo } from "@/shared/ui/display";
import { validateAdminPlacePhoto } from "../_lib/validation";

export function PlacePhotoEditor({
  file,
  onChange,
  existingUrl = null,
  removed = false,
  onRemoved,
  error,
}: {
  file: File | null;
  onChange: (file: File | null) => void;
  existingUrl?: string | null;
  removed?: boolean;
  onRemoved?: (removed: boolean) => void;
  error?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const ownedUrl = useRef<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      if (ownedUrl.current) URL.revokeObjectURL(ownedUrl.current);
    };
  }, []);

  const replacePreview = (next: File) => {
    if (ownedUrl.current) URL.revokeObjectURL(ownedUrl.current);
    const url = URL.createObjectURL(next);
    ownedUrl.current = url;
    setPreview(url);
  };

  const clearPreview = () => {
    if (ownedUrl.current) URL.revokeObjectURL(ownedUrl.current);
    ownedUrl.current = null;
    setPreview(null);
  };

  const pick = (next: File | undefined) => {
    if (inputRef.current) inputRef.current.value = "";
    if (!next) return;
    const checked = validateAdminPlacePhoto(next);
    if (!checked.valid) {
      setLocalError(checked.issues[0]?.message ?? "사진을 확인해 주세요.");
      return;
    }
    setLocalError(null);
    replacePreview(next);
    onChange(next);
  };

  const currentUrl = existingUrl && !removed && !preview ? existingUrl : null;

  return (
    <section id="admin-place-photo" tabIndex={-1} className="space-y-3">
      <div>
        <h2 className="text-[15px] font-bold">대표 사진</h2>
        <p className="mt-1 text-[13px] text-ink-sub">선택 항목이에요. JPEG, PNG, WEBP, 최대 10MB, 1장만 등록할 수 있어요.</p>
      </div>
      <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" aria-label="대표 사진 파일" onChange={(event) => pick(event.target.files?.[0])} />
      {currentUrl ? (
        <div className="flex items-start gap-3">
          <div className="relative size-24 shrink-0 overflow-hidden rounded-card">
            <Image src={currentUrl} alt="현재 대표 사진" fill sizes="96px" unoptimized className="object-cover" />
          </div>
          <div className="min-w-0">
            <p className="text-[13px] text-ink-sub">저장하기 전에는 이 사진을 유지해요.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
                사진 교체
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => onRemoved?.(true)}>
                기존 사진 제거
              </Button>
            </div>
          </div>
        </div>
      ) : preview ? (
        <div className="flex items-start gap-3">
          <div className="relative size-24 shrink-0 overflow-hidden rounded-card">
            <Photo src={preview} alt="선택한 대표 사진 미리보기" sizes="96px" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-[13px] font-bold">{file?.name}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
                사진 교체
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  clearPreview();
                  onChange(null);
                }}
              >
                선택한 사진 취소
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => inputRef.current?.click()} className="flex h-24 w-full flex-col items-center justify-center gap-1 rounded-card border border-dashed border-line text-[13px] text-ink-sub">
          <ImagePlus size={18} aria-hidden />
          사진 추가
        </button>
      )}
      {(localError || error) && (
        <p className="text-[12px] text-danger" role="alert">
          {localError || error}
        </p>
      )}
    </section>
  );
}
