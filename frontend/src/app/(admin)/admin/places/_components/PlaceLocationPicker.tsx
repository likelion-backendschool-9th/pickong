"use client";

import { Crosshair } from "lucide-react";
import { useState } from "react";
import { MapView, type LatLng } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { Field, Input } from "@/shared/ui/form";

/** 회원 장소 등록과 같은 기본 지역(성수)입니다. 이 좌표는 선택으로 확정하지 않습니다. */
const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };

export function PlaceLocationPicker({
  location,
  onChange,
  error,
}: {
  location: LatLng | null;
  onChange: (location: LatLng) => void;
  error?: string;
}) {
  const [camera] = useState<LatLng>(location ?? DEFAULT_CENTER);
  const [draft, setDraft] = useState<LatLng>(camera);
  const [latText, setLatText] = useState("");
  const [lngText, setLngText] = useState("");
  const [manualError, setManualError] = useState<string | null>(null);

  const applyManual = () => {
    const lat = parseAxis(latText);
    const lng = parseAxis(lngText);
    if (lat == null || lng == null) {
      setManualError("위도와 경도를 숫자로 입력해 주세요.");
      return;
    }
    setManualError(null);
    onChange({ lat, lng });
  };

  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-[15px] font-bold">위치</h2>
        <p className="mt-1 text-[13px] leading-relaxed text-ink-sub">지도를 움직인 뒤 가운데 핀의 위치를 지정해 주세요. 지도를 움직이는 것만으로는 좌표가 저장되지 않아요.</p>
        <p className="mt-1 text-[13px] leading-relaxed text-ink-sub">입력한 주소와 지도에서 선택한 위치가 일치하는지 확인해 주세요.</p>
      </div>
      <MapView className="h-64 rounded-card" center={camera} metersPerPixel={2.4} crosshair onCenterChange={setDraft} ariaLabel="장소 위치 선택 지도" />
      <div className="flex items-center gap-2 rounded-card bg-soft px-4 py-3 text-[13px]">
        <Crosshair size={16} className="shrink-0 text-primary-deep" aria-hidden />
        <span className="font-bold">지도 중심</span>
        <span className="text-ink-sub tabular-nums">
          {draft.lat.toFixed(5)}, {draft.lng.toFixed(5)}
        </span>
      </div>
      <Button type="button" variant="secondary" size="md" onClick={() => onChange(draft)}>
        이 위치로 지정
      </Button>
      <p className="text-[13px] text-ink-sub" aria-live="polite">
        {location ? `지정한 위치 ${location.lat}, ${location.lng}` : "아직 위치를 지정하지 않았어요."}
      </p>
      {error && (
        <p id="admin-place-location-error" tabIndex={-1} className="text-[12px] text-danger" role="alert">
          {error}
        </p>
      )}
      <div className="rounded-card border border-line p-3">
        <p className="text-[13px] font-bold">지도를 쓸 수 없을 때</p>
        <p className="mt-1 text-[12px] leading-relaxed text-ink-sub">위도와 경도를 직접 입력할 수 있어요. 저장 전에는 같은 범위 검사를 합니다.</p>
        <div className="mt-3 grid grid-cols-2 gap-2">
          <Field label="위도" htmlFor="admin-place-lat">
            <Input id="admin-place-lat" inputMode="decimal" value={latText} onChange={(event) => setLatText(event.target.value)} placeholder="37.5446" />
          </Field>
          <Field label="경도" htmlFor="admin-place-lng">
            <Input id="admin-place-lng" inputMode="decimal" value={lngText} onChange={(event) => setLngText(event.target.value)} placeholder="127.0525" />
          </Field>
        </div>
        {manualError && (
          <p className="mt-2 text-[12px] text-danger" role="alert">
            {manualError}
          </p>
        )}
        <Button type="button" variant="outline" size="sm" className="mt-3" onClick={applyManual}>
          입력한 좌표 사용
        </Button>
      </div>
    </section>
  );
}

function parseAxis(value: string): number | null {
  const text = value.trim();
  if (!text) return null;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}
