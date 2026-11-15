"use client";

import { ChevronRight, MapPin, MapPinOff } from "lucide-react";
import { useMemo, useState } from "react";
import { useSavedPlaces, useSavePlaces, type Place, type SavePlacesInput } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { SelectRow } from "@/shared/ui/controls";
import { Badge, Notice, SectionTitle } from "@/shared/ui/display";
import { useToast } from "@/shared/ui/Toast";
import { CollectionPicker } from "./CollectionPicker";
import { describeSaveResult } from "./model";

interface SavePlaceSheetProps {
  open: boolean;
  onClose: () => void;
  /** 저장 후보 장소 (중복 제거된 목록) */
  places: Place[];
  /**
   * 게시물에서 열 때: 지금 보고 있는 사진의 장소 (없으면 null).
   * 지정하면 "현재 장소 / 전체 장소" 선택 단계부터 시작합니다.
   */
  currentPlace?: Place | null;
  source?: SavePlacesInput["source"];
}

type Step = { name: "choose" } | { name: "collection"; mode: "current" | "all" };

/**
 * 장소 찜콩: 현재 사진의 장소 또는 게시물 전체 장소 → 컬렉션 선택 → 저장.
 * 이미 저장된 장소는 다시 만들지 않고 선택한 컬렉션에만 연결합니다.
 */
export function SavePlaceSheet(props: SavePlaceSheetProps) {
  return (
    <BottomSheet open={props.open} onClose={props.onClose} showClose={false}>
      {props.open && <SaveFlow {...props} />}
    </BottomSheet>
  );
}

function initialStep({ places, currentPlace }: SavePlaceSheetProps): Step {
  if (currentPlace !== undefined && places.length > 1) return { name: "choose" };
  if (places.length === 1) return { name: "collection", mode: "current" };
  return { name: "collection", mode: "all" };
}

function SaveFlow(props: SavePlaceSheetProps) {
  const { places, currentPlace, onClose, source } = props;
  const [step, setStep] = useState<Step>(() => initialStep(props));
  const { data: saved = [] } = useSavedPlaces();
  const savedByPlace = useMemo(() => new Map(saved.map((s) => [s.place.id, s])), [saved]);
  const alreadyCount = places.filter((p) => savedByPlace.has(p.id)).length;

  if (step.name === "choose") {
    return (
      <div className="pb-2">
        <SheetHeader title="어떤 장소를 찜콩할까요?" description="사진마다 연결된 장소가 달라요." onClose={onClose} />
        <div className="space-y-2">
          <ChoiceCard
            disabled={!currentPlace}
            icon={currentPlace ? <MapPin size={20} /> : <MapPinOff size={20} />}
            title="현재 장소 찜콩하기"
            description={
              currentPlace
                ? `${currentPlace.name} · 지금 보고 있는 사진`
                : "이 사진에는 연결된 장소가 없어요"
            }
            onClick={() => setStep({ name: "collection", mode: "current" })}
          />
          <ChoiceCard
            icon={<MapPin size={20} />}
            title="전체 장소 찜콩하기"
            description={`중복 제외 ${places.length}곳${alreadyCount ? ` · 이미 저장된 장소 ${alreadyCount}곳` : ""}`}
            onClick={() => setStep({ name: "collection", mode: "all" })}
          />
        </div>
        <p className="mt-4 text-[12px] text-ink-faint">게시물 저장(북마크)과는 별개로, 실제 장소를 내 지도에 저장해요.</p>
      </div>
    );
  }

  const targets = step.mode === "current" ? [currentPlace ?? places[0]].filter((p): p is Place => !!p) : places;
  return (
    <CollectionStep
      key={step.mode}
      targets={targets}
      mode={step.mode}
      savedByPlace={savedByPlace}
      onClose={onClose}
      onBack={initialStep(props).name === "choose" ? () => setStep({ name: "choose" }) : undefined}
      source={source}
    />
  );
}

function CollectionStep({
  targets,
  mode,
  savedByPlace,
  onClose,
  onBack,
  source,
}: {
  targets: Place[];
  mode: "current" | "all";
  savedByPlace: Map<string, { collectionIds: string[] }>;
  onClose: () => void;
  onBack?: () => void;
  source?: SavePlacesInput["source"];
}) {
  const toast = useToast();
  const save = useSavePlaces();
  const [selectedPlaces, setSelectedPlaces] = useState<string[]>(targets.map((p) => p.id));
  const [collectionIds, setCollectionIds] = useState<string[]>([]);

  const single = mode === "current" ? targets[0] : null;
  const singleSaved = single ? savedByPlace.get(single.id) : undefined;

  const togglePlace = (id: string) =>
    setSelectedPlaces((prev) => (prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]));

  const submit = () => {
    save.mutate(
      { placeIds: selectedPlaces, collectionIds, source },
      {
        onSuccess: (result) => {
          const { message, detail } = describeSaveResult(result);
          toast({ message, detail, action: { label: "내 지도", href: routes.map() } });
          onClose();
        },
      },
    );
  };

  const count = selectedPlaces.length;
  const ctaLabel = single
    ? singleSaved
      ? "선택한 컬렉션에 연결"
      : collectionIds.length
        ? "컬렉션에 저장"
        : "내 지도에 저장"
    : `선택한 ${count}곳 저장`;

  return (
    <div className="pb-[env(safe-area-inset-bottom)]">
      <SheetHeader
        title={single ? "어디에 저장할까요?" : `게시물 속 장소 ${targets.length}곳`}
        description={single ? undefined : "사진 순서 · 중복 장소는 한 번만 저장돼요"}
        onClose={onClose}
        onBack={onBack}
      />

      {single ? (
        <div className="mb-4 rounded-card bg-soft px-4 py-3">
          <div className="text-[15px] font-bold">{single.name}</div>
          <div className="mt-0.5 text-[12px] text-ink-sub">
            {single.region} · {singleSaved ? "이미 내 지도에 있어요 · 컬렉션만 연결돼요" : "새로 저장"}
          </div>
        </div>
      ) : (
        <div className="-mx-3 mb-3">
          {targets.map((p) => {
            const already = savedByPlace.has(p.id);
            return (
              <SelectRow
                key={p.id}
                checked={selectedPlaces.includes(p.id)}
                onToggle={() => togglePlace(p.id)}
                title={p.name}
                description={already ? "이미 저장됨 · 컬렉션 연결만 추가" : `${p.region} · 새로 저장`}
                trailing={already ? <Badge tone="neutral">저장됨</Badge> : null}
              />
            );
          })}
        </div>
      )}

      <SectionTitle className="mb-1">저장할 컬렉션</SectionTitle>
      <p className="mb-2 text-[12px] text-ink-faint">선택하지 않으면 내 지도에만 저장돼요.</p>
      <CollectionPicker
        selected={collectionIds}
        onChange={setCollectionIds}
        lockedIds={singleSaved?.collectionIds ?? []}
      />

      {save.isError && (
        <Notice tone="danger" className="mt-3">
          {errorMessage(save.error)}
        </Notice>
      )}

      <Button
        block
        className="mt-4 mb-3"
        onClick={submit}
        loading={save.isPending}
        disabled={count === 0 || (!!singleSaved && collectionIds.length === 0)}
      >
        {ctaLabel}
      </Button>
    </div>
  );
}

function SheetHeader({
  title,
  description,
  onClose,
  onBack,
}: {
  title: string;
  description?: string;
  onClose: () => void;
  onBack?: () => void;
}) {
  return (
    <div className="mb-4 flex items-start gap-2 pt-1">
      <div className="min-w-0 flex-1">
        {onBack && (
          <button type="button" onClick={onBack} className="mb-1 text-[12px] font-medium text-ink-sub">
            ← 다시 선택
          </button>
        )}
        <h2 className="text-[18px] font-bold">{title}</h2>
        {description && <p className="mt-1 text-[13px] text-ink-sub">{description}</p>}
      </div>
      <button type="button" onClick={onClose} aria-label="닫기" className="-mr-2 inline-flex size-10 items-center justify-center rounded-full text-[22px] leading-none text-ink active:bg-fill">
        ×
      </button>
    </div>
  );
}

function ChoiceCard({
  icon,
  title,
  description,
  onClick,
  disabled,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "flex w-full items-center gap-3 rounded-card px-4 py-4 text-left transition-colors",
        disabled ? "cursor-not-allowed bg-fill text-ink-faint" : "bg-soft active:bg-selected",
      )}
    >
      <span className={cn("shrink-0", disabled ? "text-ink-faint" : "text-primary-deep")}>{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-bold">{title}</span>
        <span className={cn("mt-0.5 block text-[12px]", disabled ? "text-ink-faint" : "text-ink-sub")}>{description}</span>
      </span>
      {!disabled && <ChevronRight size={18} className="shrink-0 text-ink-faint" />}
    </button>
  );
}
