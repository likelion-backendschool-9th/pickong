"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useCollection, useDeleteCollection, useUpdateCollection, type CollectionDetail } from "@/entities/collection";
import { PlaceListItem } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { ErrorState, Notice, SectionTitle, Skeleton } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { CollectionForm, type CollectionFormValue } from "../../_components/CollectionForm";

export function EditCollectionScreen({ id }: { id: string }) {
  const { data, isPending, isError, error } = useCollection(id);
  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  return <EditForm collection={data} />;
}

function EditForm({ collection }: { collection: CollectionDetail }) {
  const router = useRouter();
  const toast = useToast();
  const update = useUpdateCollection(collection.id);
  const remove = useDeleteCollection();
  const [value, setValue] = useState<CollectionFormValue>({
    title: collection.title,
    description: collection.description,
    visibility: collection.visibility,
    pinColor: collection.pinColor,
  });
  const [removed, setRemoved] = useState<string[]>([]);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const save = () =>
    update.mutate(
      { ...value, removePlaceIds: removed },
      {
        onSuccess: () => {
          toast({ message: "변경 내용을 저장했어요" });
          router.replace(routes.collection(collection.id));
        },
      },
    );

  return (
    <Screen>
      <TopBar title="컬렉션 편집" backHref={routes.collection(collection.id)} />
      <div className="px-5 pt-2 pb-6">
        <CollectionForm value={value} onChange={setValue} />

        <SectionTitle className="mt-8 mb-1">장소 {collection.places.length - removed.length}곳 관리</SectionTitle>
        <p className="text-[12px] text-ink-faint">컬렉션에서 빼도 저장한 장소는 내 지도에 남아요.</p>
        <ul className="mt-2 divide-y divide-line">
          {collection.places.map((p) => {
            const isRemoved = removed.includes(p.id);
            return (
              <li key={p.id} className={isRemoved ? "opacity-40" : undefined}>
                <PlaceListItem
                  place={p}
                  trailing={
                    <Button
                      size="sm"
                      variant={isRemoved ? "secondary" : "outline"}
                      onClick={() => setRemoved((r) => (isRemoved ? r.filter((x) => x !== p.id) : [...r, p.id]))}
                    >
                      {isRemoved ? "되돌리기" : "빼기"}
                    </Button>
                  }
                />
              </li>
            );
          })}
        </ul>

        <button type="button" onClick={() => setConfirmDelete(true)} className="mt-8 text-[13px] font-medium text-danger">
          컬렉션 삭제
        </button>
        {update.isError && <Notice tone="danger" className="mt-4">{errorMessage(update.error)}</Notice>}
      </div>
      <BottomCta>
        <Button block onClick={save} loading={update.isPending} disabled={!value.title.trim()}>
          변경 내용 저장
        </Button>
      </BottomCta>

      <BottomSheet open={confirmDelete} onClose={() => setConfirmDelete(false)} title={`‘${collection.title}’ 컬렉션을 삭제할까요?`}>
        <Notice>컬렉션만 삭제돼요. 안에 있던 장소 {collection.places.length}곳은 내 지도에 그대로 남아요.</Notice>
        <div className="mt-4 mb-3 grid grid-cols-2 gap-2">
          <Button variant="outline" onClick={() => setConfirmDelete(false)}>취소</Button>
          <Button
            variant="danger"
            loading={remove.isPending}
            onClick={() =>
              remove.mutate(collection.id, {
                onSuccess: () => {
                  toast({ message: "컬렉션을 삭제했어요", detail: "장소는 내 지도에 남아 있어요" });
                  router.replace(routes.collections());
                },
              })
            }
          >
            삭제
          </Button>
        </div>
      </BottomSheet>
    </Screen>
  );
}
