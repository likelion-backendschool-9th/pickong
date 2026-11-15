"use client";

import { Crosshair, ImagePlus, X } from "lucide-react";
import { useRef, useState, type ReactNode } from "react";
import { CATEGORY_LABEL, FILTER_CATEGORIES, useCreateCustomPlace, type Place, type PlaceCategory } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { visibilityLabel, type Visibility } from "@/shared/config/visibility";
import { MapView, type LatLng } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { Chip } from "@/shared/ui/controls";
import { Notice, Photo } from "@/shared/ui/display";
import { Field, Input, SelectTrigger, Textarea } from "@/shared/ui/form";
import { VisibilitySheet } from "@/shared/ui/VisibilitySheet";

const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };

interface CustomPlaceFlowProps {
  /** 장소가 만들어진 뒤 호출. Promise를 돌려주면 끝날 때까지 버튼이 로딩 상태입니다. */
  onCreated: (place: Place) => void | Promise<void>;
  initialName?: string;
  submitLabel?: string;
  /** 입력 단계 하단에 추가로 보여줄 내용 (예: 컬렉션 선택) */
  extra?: ReactNode;
}

/**
 * 미등록 장소 직접 추가: 지도에서 좌표 확정 → 이름·설명·사진·공개 범위 입력.
 * 개인 좌표로 만든 장소는 비공개가 기본값입니다.
 */
export function CustomPlaceFlow({ onCreated, initialName = "", submitLabel = "내 지도에 저장", extra }: CustomPlaceFlowProps) {
  const [step, setStep] = useState<"pick" | "form">("pick");
  const [center, setCenter] = useState<LatLng>(DEFAULT_CENTER);
  const [confirmed, setConfirmed] = useState<LatLng | null>(null);

  if (step === "pick" || !confirmed) {
    return (
      <div>
        <h2 className="text-[18px] font-bold">지도를 움직여 위치를 맞춰 주세요</h2>
        <p className="mt-1 text-[13px] text-ink-sub">가운데 핀이 가리키는 곳으로 저장돼요.</p>
        <MapView
          className="-mx-5 mt-4 h-[46dvh] min-h-72"
          center={confirmed ?? DEFAULT_CENTER}
          metersPerPixel={2.4}
          crosshair
          onCenterChange={setCenter}
          caption="예시 지도 · 좌표 지정"
        />
        <div className="mt-4 flex items-center gap-2 rounded-card bg-soft px-4 py-3 text-[13px]">
          <Crosshair size={16} className="text-primary-deep" />
          <span className="font-bold">선택한 좌표</span>
          <span className="text-ink-sub tabular-nums">
            {center.lat.toFixed(5)}, {center.lng.toFixed(5)}
          </span>
        </div>
        <Button
          block
          className="mt-4"
          onClick={() => {
            setConfirmed(center);
            setStep("form");
          }}
        >
          이 위치로 지정
        </Button>
      </div>
    );
  }

  return (
    <DetailsForm
      location={confirmed}
      onRepick={() => setStep("pick")}
      onCreated={onCreated}
      initialName={initialName}
      submitLabel={submitLabel}
      extra={extra}
    />
  );
}

function DetailsForm({
  location,
  onRepick,
  onCreated,
  initialName,
  submitLabel,
  extra,
}: {
  location: LatLng;
  onRepick: () => void;
  onCreated: CustomPlaceFlowProps["onCreated"];
  initialName: string;
  submitLabel: string;
  extra?: ReactNode;
}) {
  const create = useCreateCustomPlace();
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<PlaceCategory>("ETC");
  const [visibility, setVisibility] = useState<Visibility>("PRIVATE");
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const submit = () =>
    create.mutate(
      { name: name.trim(), lat: location.lat, lng: location.lng, description, category, visibility, imageUrl },
      {
        onSuccess: async (place) => {
          setFinishing(true);
          try {
            await onCreated(place);
          } finally {
            setFinishing(false);
          }
        },
      },
    );

  return (
    <div className="space-y-6">
      <Field label="장소 이름" htmlFor="cp-name">
        <Input id="cp-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 노을이 예쁜 서울숲 벤치" maxLength={40} autoFocus />
      </Field>
      <Field label="선택 위치">
        <div className="flex items-center justify-between rounded-control bg-soft px-4 py-3 text-[13px]">
          <span className="tabular-nums text-ink-sub">
            {location.lat.toFixed(5)}, {location.lng.toFixed(5)}
          </span>
          <button type="button" onClick={onRepick} className="font-bold text-primary-deep">
            좌표 다시 선택 →
          </button>
        </div>
      </Field>
      <Field label="설명" htmlFor="cp-desc" optional>
        <Textarea id="cp-desc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="나무 사이로 햇살이 들어오는 자리" className="min-h-20" maxLength={200} />
      </Field>
      <Field label="사진" optional>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) setImageUrl(URL.createObjectURL(file));
          }}
        />
        {imageUrl ? (
          <div className="relative size-24 overflow-hidden rounded-card">
            <Photo src={imageUrl} alt="선택한 사진" sizes="96px" />
            <button type="button" aria-label="사진 삭제" onClick={() => setImageUrl(null)} className="absolute top-1 right-1 rounded-full bg-black/50 p-0.5 text-white">
              <X size={14} />
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => fileRef.current?.click()} className="flex size-24 flex-col items-center justify-center gap-1 rounded-card border border-dashed border-line text-[12px] text-ink-sub">
            <ImagePlus size={20} /> 사진 추가
          </button>
        )}
      </Field>
      <Field label="카테고리">
        <div className="flex flex-wrap gap-1.5">
          {[...FILTER_CATEGORIES, "ETC" as const].map((c) => (
            <Chip key={c} selected={category === c} onClick={() => setCategory(c)}>
              {CATEGORY_LABEL[c]}
            </Chip>
          ))}
        </div>
      </Field>
      <Field label="공개 범위" htmlFor="cp-vis" hint="직접 지정한 장소는 기본적으로 나만 볼 수 있어요.">
        <SelectTrigger id="cp-vis" value={visibilityLabel(visibility)} onClick={() => setVisibilityOpen(true)} />
      </Field>
      {extra}
      {create.isError && <Notice tone="danger">{errorMessage(create.error)}</Notice>}
      <Button block onClick={submit} disabled={!name.trim()} loading={create.isPending || finishing}>
        {submitLabel}
      </Button>
      <VisibilitySheet open={visibilityOpen} value={visibility} onClose={() => setVisibilityOpen(false)} onChange={setVisibility} />
    </div>
  );
}
