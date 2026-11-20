"use client";

import { Check, ImagePlus } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { usePlaceDetail } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatDotDate } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Notice, Photo, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useAddPlacePhotos, useDeletePlacePhotos, usePlacePhotos } from "../_api/myRecordApi";

const MAX_PHOTOS = 20;

/** 저장한 장소에 나만 보는 사진 남기기: 추가 · 선택해서 삭제 */
export function PlacePhotosScreen({ placeId }: { placeId: string }) {
  const { data: detail } = usePlaceDetail(placeId);
  const { data: photos, isPending, isError, error, refetch } = usePlacePhotos(placeId);
  const add = useAddPlacePhotos(placeId);
  const remove = useDeletePlacePhotos(placeId);
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [selecting, setSelecting] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);

  const notSaved = detail && !detail.saved;
  const count = photos?.length ?? 0;

  const pick = (e: ChangeEvent<HTMLInputElement>) => {
    const files = [...(e.target.files ?? [])].slice(0, MAX_PHOTOS - count);
    e.target.value = "";
    if (!files.length) return;
    // 업로드 API 연결 전까지는 브라우저 임시 URL (새로고침하면 사라짐)
    add.mutate(
      files.map((f) => URL.createObjectURL(f)),
      { onSuccess: (created) => toast({ message: `사진 ${created.length}장을 추가했어요` }) },
    );
  };

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const stopSelecting = () => {
    setSelecting(false);
    setSelected([]);
  };

  return (
    <Screen>
      <TopBar
        title={detail ? `${detail.place.name} · 내 사진` : "내 사진"}
        backHref={routes.place(placeId)}
        right={
          count > 0 && (
            <button type="button" onClick={selecting ? stopSelecting : () => setSelecting(true)} className="h-11 px-3 text-[14px] font-medium text-ink-sub">
              {selecting ? "취소" : "선택"}
            </button>
          )
        }
      />
      <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={pick} />

      <div className="flex-1 px-5 pt-1 pb-6">
        <p className="text-[13px] text-ink-sub">나만 볼 수 있는 사진이에요 · {count}/{MAX_PHOTOS}장</p>
        {notSaved && (
          <Notice tone="warning" className="mt-3">
            찜콩한 장소에만 사진을 남길 수 있어요. 먼저 장소를 내 지도에 저장해 주세요.
          </Notice>
        )}
        {add.isError && <Notice tone="danger" className="mt-3">{errorMessage(add.error)}</Notice>}

        {isPending ? (
          <Skeleton className="mt-4 h-40" />
        ) : isError ? (
          <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
        ) : count === 0 ? (
          <EmptyState icon={<ImagePlus size={24} />} title="아직 남긴 사진이 없어요" description="다녀온 날의 분위기, 메뉴판, 찾아가는 길을 사진으로 남겨 두세요." />
        ) : (
          <ul className="-mx-1 mt-4 grid grid-cols-3 gap-1">
            {photos.map((p) => {
              const on = selected.includes(p.id);
              return (
                <li key={p.id} className="px-0">
                  <button
                    type="button"
                    disabled={!selecting}
                    onClick={() => toggle(p.id)}
                    aria-pressed={selecting ? on : undefined}
                    aria-label={`${formatDotDate(p.createdAt)} 사진${selecting ? (on ? " 선택됨" : "") : ""}`}
                    className={cn("relative block aspect-square w-full overflow-hidden bg-fill", on && "ring-2 ring-primary-deep ring-inset")}
                  >
                    <Photo src={p.url} alt="" sizes="160px" />
                    {selecting && (
                      <span
                        className={cn(
                          "absolute top-1.5 right-1.5 flex size-6 items-center justify-center rounded-full border-2 border-white",
                          on ? "bg-primary-deep text-white" : "bg-black/20 text-transparent",
                        )}
                        aria-hidden
                      >
                        <Check size={14} strokeWidth={3} />
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <BottomCta>
        {selecting ? (
          <Button
            block
            variant="danger"
            disabled={!selected.length}
            loading={remove.isPending}
            onClick={() =>
              remove.mutate(selected, {
                onSuccess: () => {
                  toast({ message: `사진 ${selected.length}장을 삭제했어요` });
                  stopSelecting();
                },
              })
            }
          >
            {selected.length ? `${selected.length}장 삭제` : "삭제할 사진을 골라 주세요"}
          </Button>
        ) : (
          <Button
            block
            icon={<ImagePlus size={18} />}
            disabled={!!notSaved || count >= MAX_PHOTOS}
            loading={add.isPending}
            onClick={() => fileRef.current?.click()}
          >
            사진 추가
          </Button>
        )}
      </BottomCta>
    </Screen>
  );
}
