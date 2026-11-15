"use client";

import { Lock, Map as MapIcon, Plus } from "lucide-react";
import { useState } from "react";
import { useCreateCollection, useMyCollections } from "@/entities/collection";
import { errorMessage } from "@/shared/api/errors";
import { visibilityLabel } from "@/shared/config/visibility";
import { Button } from "@/shared/ui/Button";
import { SelectRow } from "@/shared/ui/controls";
import { Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";

interface CollectionPickerProps {
  selected: string[];
  onChange: (ids: string[]) => void;
  /** 이미 연결된 컬렉션 (선택 해제 불가) */
  lockedIds?: string[];
}

/** 컬렉션 선택: 이름 · 공개 범위 · 장소 수 · 새 컬렉션 만들기 */
export function CollectionPicker({ selected, onChange, lockedIds = [] }: CollectionPickerProps) {
  const { data: collections, isPending } = useMyCollections();
  const create = useCreateCollection();
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");

  const toggle = (id: string) =>
    onChange(selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id]);

  const submitNew = () => {
    if (!title.trim()) return;
    create.mutate(
      { title: title.trim(), description: "", visibility: "PRIVATE", pinColor: "LIME" },
      {
        onSuccess: (c) => {
          onChange([...selected, c.id]);
          setTitle("");
          setCreating(false);
        },
      },
    );
  };

  if (isPending) {
    return (
      <div className="space-y-2">
        <Skeleton className="h-14" />
        <Skeleton className="h-14" />
      </div>
    );
  }

  return (
    <div className="-mx-3">
      {collections?.map((c) => {
        const locked = lockedIds.includes(c.id);
        return (
          <SelectRow
            key={c.id}
            checked={locked || selected.includes(c.id)}
            disabled={locked}
            onToggle={() => toggle(c.id)}
            leading={<MapIcon size={20} strokeWidth={1.6} className="shrink-0 text-ink-sub" />}
            title={c.title}
            description={locked ? "이미 연결된 컬렉션" : `${visibilityLabel(c.visibility)} · ${c.placeCount}곳`}
            trailing={c.visibility === "PRIVATE" ? <Lock size={14} className="text-ink-faint" aria-label="비공개" /> : null}
          />
        );
      })}

      {creating ? (
        <div className="mt-2 flex gap-2 px-3">
          <Input
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submitNew()}
            placeholder="새 컬렉션 이름"
            aria-label="새 컬렉션 이름"
            maxLength={30}
          />
          <Button size="lg" variant="secondary" onClick={submitNew} loading={create.isPending} disabled={!title.trim()}>
            만들기
          </Button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="mt-1 flex h-12 w-full items-center gap-2 px-3 text-[14px] font-bold text-primary-deep"
        >
          <Plus size={18} /> 새 컬렉션 만들기
        </button>
      )}
      {create.isError && <p className="px-3 text-[12px] text-danger">{errorMessage(create.error)}</p>}
    </div>
  );
}
