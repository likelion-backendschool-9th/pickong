"use client";

import { CheckCircle2, FolderInput, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useMyCollections } from "@/entities/collection";
import { PlaceListItem, useDeleteSavedPlaces, useUpdateSavedPlaces, type SavedPlace } from "@/entities/place";
import { CollectionPicker } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { CheckMark, Chip, Tabs } from "@/shared/ui/controls";
import { Badge, EmptyState, Notice } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";
import type { SavedPlaceFilter } from "../_lib/filterSavedPlaces";

type Action = "collection" | "delete" | null;

/** 저장 장소 목록: 지도와 같은 데이터. 선택 관리로 컬렉션 변경 · 방문 상태 변경 · 삭제 */
export function SavedListView({
  saved,
  total,
  filter,
  onFilter,
}: {
  saved: SavedPlace[];
  total: number;
  filter: SavedPlaceFilter;
  onFilter: (patch: Partial<SavedPlaceFilter>) => void;
}) {
  const [managing, setManaging] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [action, setAction] = useState<Action>(null);
  const { data: collections = [] } = useMyCollections();
  const update = useUpdateSavedPlaces();
  const toast = useToast();

  const toggle = (id: string) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const finish = () => {
    setSelected([]);
    setManaging(false);
    setAction(null);
  };
  const collectionNames = (ids: string[]) =>
    ids.map((id) => collections.find((c) => c.id === id)?.title).filter(Boolean).join(", ");

  const markVisited = () =>
    update.mutate(
      { ids: selected, visitStatus: "VISITED" },
      { onSuccess: () => { toast({ message: `${selected.length}곳을 방문 완료로 표시했어요` }); finish(); } },
    );

  return (
    <div>
      <div className="flex items-center justify-between px-4 pt-1">
        <Tabs
          size="sm"
          items={[
            { value: "ALL", label: `전체 ${total}` },
            { value: "WANT_TO_GO", label: "가고 싶은 곳" },
            { value: "VISITED", label: "방문 완료" },
          ]}
          value={filter.visit}
          onChange={(v) => onFilter({ visit: v })}
        />
        <Chip selected={managing} onClick={() => (managing ? finish() : setManaging(true))}>
          {managing ? "완료" : "선택 관리"}
        </Chip>
      </div>
      <p className="px-4 pt-2 text-[12px] text-ink-faint">최근 저장순 · {saved.length}곳</p>

      {total === 0 ? (
        <EmptyState title="아직 저장한 장소가 없어요" description="위에서 장소를 검색하거나 지도에서 골라 찜콩해 보세요." />
      ) : saved.length === 0 ? (
        <EmptyState title="조건에 맞는 장소가 없어요" description="필터를 바꾸거나 검색어를 지워 보세요." />
      ) : (
        <ul className="divide-y divide-line px-4 pb-24">
          {saved.map((s) => (
            <li key={s.id} className="flex items-center gap-3">
              {managing && (
                <button type="button" role="checkbox" aria-checked={selected.includes(s.id)} aria-label={`${s.place.name} 선택`} onClick={() => toggle(s.id)} className="py-3">
                  <CheckMark checked={selected.includes(s.id)} round />
                </button>
              )}
              <div className="min-w-0 flex-1">
                {managing ? (
                  <PlaceListItem
                    place={s.place}
                    onClick={() => toggle(s.id)}
                    meta={collectionNames(s.collectionIds) || "컬렉션 없음"}
                  />
                ) : (
                  <Link href={routes.place(s.place.id)}>
                    <PlaceListItem
                      place={s.place}
                      meta={`${collectionNames(s.collectionIds) || "컬렉션 없음"} · ${s.source.label}`}
                      trailing={s.visitStatus === "VISITED" ? <Badge tone="neutral">방문 완료</Badge> : null}
                    />
                  </Link>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {managing && (
        <div className="fixed inset-x-0 bottom-[calc(60px+env(safe-area-inset-bottom))] z-30 mx-auto w-full max-w-[480px] border-t border-line bg-bg px-4 py-3">
          <p className="mb-2 text-[13px] font-bold">선택한 장소 {selected.length}곳</p>
          <div className="grid grid-cols-3 gap-2">
            <Button size="sm" variant="secondary" disabled={!selected.length} onClick={() => setAction("collection")} icon={<FolderInput size={15} />}>
              컬렉션
            </Button>
            <Button size="sm" variant="secondary" disabled={!selected.length} loading={update.isPending} onClick={markVisited} icon={<CheckCircle2 size={15} />}>
              방문 완료
            </Button>
            <Button size="sm" variant="danger" disabled={!selected.length} onClick={() => setAction("delete")} icon={<Trash2 size={15} />}>
              삭제
            </Button>
          </div>
        </div>
      )}

      <BottomSheet open={action === "collection"} onClose={() => setAction(null)} title={`${selected.length}곳을 컬렉션에 추가`}>
        {action === "collection" && <MoveToCollection ids={selected} onDone={finish} />}
      </BottomSheet>
      <BottomSheet open={action === "delete"} onClose={() => setAction(null)} title={`${selected.length}곳을 내 지도에서 삭제할까요?`}>
        {action === "delete" && <ConfirmDelete ids={selected} onDone={finish} onCancel={() => setAction(null)} />}
      </BottomSheet>
    </div>
  );
}

function MoveToCollection({ ids, onDone }: { ids: string[]; onDone: () => void }) {
  const [collectionIds, setCollectionIds] = useState<string[]>([]);
  const [mode, setMode] = useState<"add" | "move">("add");
  const update = useUpdateSavedPlaces();
  const toast = useToast();
  return (
    <div className="pb-[env(safe-area-inset-bottom)]">
      <Tabs
        size="sm"
        className="mb-3"
        items={[
          { value: "add", label: "기존 컬렉션 유지 + 추가" },
          { value: "move", label: "선택한 컬렉션으로 이동" },
        ]}
        value={mode}
        onChange={setMode}
      />
      <CollectionPicker selected={collectionIds} onChange={setCollectionIds} />
      <Button
        block
        className="mt-4 mb-3"
        disabled={collectionIds.length === 0}
        loading={update.isPending}
        onClick={() =>
          update.mutate(
            mode === "add" ? { ids, addCollectionIds: collectionIds } : { ids, setCollectionIds: collectionIds },
            { onSuccess: () => { toast({ message: mode === "add" ? "컬렉션에 추가했어요" : "컬렉션을 옮겼어요" }); onDone(); } },
          )
        }
      >
        {mode === "add" ? "컬렉션에 추가" : "컬렉션 이동"}
      </Button>
    </div>
  );
}

function ConfirmDelete({ ids, onDone, onCancel }: { ids: string[]; onDone: () => void; onCancel: () => void }) {
  const remove = useDeleteSavedPlaces();
  const toast = useToast();
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <Notice tone="warning">삭제한 장소는 내 지도와 모든 컬렉션에서 빠져요. 일정에 넣어 둔 장소는 그대로 남아요.</Notice>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Button variant="outline" onClick={onCancel}>취소</Button>
        <Button
          variant="danger"
          loading={remove.isPending}
          onClick={() => remove.mutate(ids, { onSuccess: () => { toast({ message: `${ids.length}곳을 삭제했어요` }); onDone(); } })}
        >
          삭제
        </Button>
      </div>
    </div>
  );
}
