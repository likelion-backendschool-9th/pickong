"use client";

import { Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ALL_CATEGORIES, CATEGORY_LABEL, usePlaceDetail, type Place } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { Field, Input, SelectTrigger, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { VisibilitySheet } from "@/shared/ui/VisibilitySheet";
import { useDeletePlace, useUpdatePlace, type UpdatePlaceInput } from "../../_api/myPlaceApi";

export function EditPlaceScreen({ placeId }: { placeId: string }) {
  const { data, isPending, isError, error } = usePlaceDetail(placeId);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  if (!data.isOwner) {
    return (
      <Screen>
        <TopBar title="장소 수정" backHref={routes.place(placeId)} />
        <ErrorState message="내가 직접 등록한 장소만 수정할 수 있어요." />
      </Screen>
    );
  }
  return <EditForm place={data.place} />;
}

function EditForm({ place }: { place: Place }) {
  const router = useRouter();
  const toast = useToast();
  const update = useUpdatePlace(place.id);
  const remove = useDeletePlace();
  const [value, setValue] = useState<UpdatePlaceInput>({
    name: place.name,
    category: place.category,
    visibility: place.visibility,
    description: place.description,
  });
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const set = <K extends keyof UpdatePlaceInput>(key: K, v: UpdatePlaceInput[K]) => setValue((s) => ({ ...s, [key]: v }));

  return (
    <Screen>
      <TopBar title="장소 수정" backHref={routes.myPlaces()} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        <Field label="장소 이름" htmlFor="place-name" hint={`${value.name.trim().length}/30`}>
          <Input id="place-name" value={value.name} maxLength={30} onChange={(e) => set("name", e.target.value)} />
        </Field>

        <div>
          <p className="mb-2 text-[13px] font-bold">카테고리</p>
          <div className="flex flex-wrap gap-2">
            {ALL_CATEGORIES.map((c) => (
              <Chip key={c} selected={value.category === c} onClick={() => set("category", c)}>
                {CATEGORY_LABEL[c]}
              </Chip>
            ))}
          </div>
        </div>

        <Field label="공개 범위" htmlFor="place-visibility">
          <SelectTrigger id="place-visibility" value={visibilityLabel(value.visibility)} onClick={() => setVisibilityOpen(true)} />
        </Field>

        <Field label="설명" htmlFor="place-desc" optional hint={`${value.description.length}/200`}>
          <Textarea
            id="place-desc"
            value={value.description}
            maxLength={200}
            onChange={(e) => set("description", e.target.value)}
            placeholder="어떤 곳인지, 언제 가면 좋은지 적어 두세요."
          />
        </Field>

        <div className="rounded-card bg-fill px-4 py-3 text-[12px] leading-relaxed text-ink-sub">
          위치(좌표)는 바꿀 수 없어요. 위치가 틀렸다면 장소를 지우고 다시 등록해 주세요.
          <br />
          {place.address}
        </div>

        {update.isError && <Notice tone="danger">{errorMessage(update.error)}</Notice>}

        <button type="button" onClick={() => setConfirmDelete(true)} className="inline-flex items-center gap-1.5 text-[14px] font-medium text-danger">
          <Trash2 size={16} /> 이 장소 삭제
        </button>
      </div>

      <BottomCta>
        <Button
          block
          disabled={!value.name.trim()}
          loading={update.isPending}
          onClick={() =>
            update.mutate(
              { ...value, name: value.name.trim() },
              {
                onSuccess: () => {
                  toast({ message: "장소를 수정했어요" });
                  router.replace(routes.place(place.id));
                },
              },
            )
          }
        >
          저장
        </Button>
      </BottomCta>

      <VisibilitySheet open={visibilityOpen} value={value.visibility} onClose={() => setVisibilityOpen(false)} onChange={(v) => set("visibility", v)} />

      <BottomSheet open={confirmDelete} onClose={() => setConfirmDelete(false)} title="장소를 삭제할까요?">
        <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
          <p className="text-[14px] leading-relaxed text-ink-sub">
            <b className="text-ink">{place.name}</b>을(를) 지우면 내 지도 · 컬렉션에서 빠지고, 남겨 둔 사진과 태그도 함께 지워져요. 되돌릴 수 없어요.
          </p>
          {remove.isError && <Notice tone="danger" className="mt-3">{errorMessage(remove.error)}</Notice>}
          <div className="mt-5 grid grid-cols-2 gap-2">
            <Button variant="outline" block onClick={() => setConfirmDelete(false)}>
              취소
            </Button>
            <Button
              variant="danger"
              block
              loading={remove.isPending}
              onClick={() =>
                remove.mutate(place.id, {
                  onSuccess: () => {
                    toast({ message: "장소를 삭제했어요" });
                    router.replace(routes.myPlaces());
                  },
                })
              }
            >
              삭제
            </Button>
          </div>
        </div>
      </BottomSheet>
    </Screen>
  );
}
