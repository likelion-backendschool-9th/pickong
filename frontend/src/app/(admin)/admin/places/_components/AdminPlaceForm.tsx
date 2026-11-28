"use client";

import { ALL_CATEGORIES, categoryLabel } from "@/entities/place";
import { Badge } from "@/shared/ui/display";
import { Field, Input } from "@/shared/ui/form";
import type { AdminPlaceExceptionInput, AdminPlaceFormValues, AdminPlaceHourInput, AdminPlaceIssue } from "../_lib/models";
import { BusinessHoursEditor } from "./BusinessHoursEditor";
import { HoursExceptionEditor } from "./HoursExceptionEditor";
import { PlaceLocationPicker } from "./PlaceLocationPicker";
import { PlacePhotoEditor } from "./PlacePhotoEditor";

export function AdminPlaceForm({
  values,
  onChange,
  file,
  onFile,
  issues,
  mode = "create",
  addressRequired = true,
  existingImageUrl = null,
  photoRemoved = false,
  onPhotoRemoved,
}: {
  values: AdminPlaceFormValues;
  onChange: (patch: Partial<AdminPlaceFormValues>) => void;
  file: File | null;
  onFile: (file: File | null) => void;
  issues: AdminPlaceIssue[];
  mode?: "create" | "edit";
  addressRequired?: boolean;
  existingImageUrl?: string | null;
  photoRemoved?: boolean;
  onPhotoRemoved?: (removed: boolean) => void;
}) {
  const message = (field: string) => issues.find((item) => item.field === field)?.message;
  const hoursError = issues.find((item) => item.field.startsWith("businessHours"))?.message;
  const exceptionError = issues.find((item) => item.field.startsWith("hoursExceptions"))?.message;

  return (
    <div className="space-y-8 px-5 pt-4 pb-8">
      <section className="space-y-3">
        <h2 className="text-[15px] font-bold">기본 정보</h2>
        {mode === "create" && (
          <div className="flex items-center gap-2">
            <Badge>시스템</Badge>
            <span className="text-[13px] text-ink-sub">시스템 제공 장소로 등록돼요.</span>
          </div>
        )}
        <Field label={<Required label="장소 이름" />} htmlFor="admin-place-name" error={message("name") && <span id="admin-place-name-error">{message("name")}</span>}>
          <Input id="admin-place-name" value={values.name} required aria-invalid={message("name") ? true : undefined} aria-describedby={message("name") ? "admin-place-name-error" : undefined} onChange={(event) => onChange({ name: event.target.value })} />
        </Field>
        <Field label={addressRequired ? <Required label="주소" /> : "주소"} htmlFor="admin-place-address" optional={!addressRequired} error={message("address") && <span id="admin-place-address-error">{message("address")}</span>}>
          <Input id="admin-place-address" value={values.address} required={addressRequired} aria-invalid={message("address") ? true : undefined} aria-describedby={message("address") ? "admin-place-address-error" : undefined} onChange={(event) => onChange({ address: event.target.value })} />
        </Field>
        <Field label={<Required label="카테고리" />} htmlFor="admin-place-category" error={message("category") && <span id="admin-place-category-error">{message("category")}</span>}>
          <select
            id="admin-place-category"
            value={values.category}
            required
            aria-invalid={message("category") ? true : undefined}
            aria-describedby={message("category") ? "admin-place-category-error" : undefined}
            onChange={(event) => onChange({ category: event.target.value })}
            className="h-12 w-full rounded-control border border-transparent bg-soft px-4 text-[15px] text-ink outline-none focus:border-primary-deep/50 focus:bg-bg"
          >
            <option value="">카테고리를 선택해 주세요</option>
            {values.category && !(ALL_CATEGORIES as readonly string[]).includes(values.category) && <option value={values.category}>{values.category}</option>}
            {ALL_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </select>
        </Field>
      </section>
      <PlaceLocationPicker location={values.location} error={message("latitude") ?? message("longitude")} onChange={(location) => onChange({ location })} />
      <BusinessHoursEditor seed={values.businessHours} error={hoursError} onChange={(businessHours: AdminPlaceHourInput[]) => onChange({ businessHours })} />
      <HoursExceptionEditor seed={values.hoursExceptions} error={exceptionError} onChange={(hoursExceptions: AdminPlaceExceptionInput[]) => onChange({ hoursExceptions })} />
      <PlacePhotoEditor file={file} existingUrl={existingImageUrl} removed={photoRemoved} onRemoved={onPhotoRemoved} error={message("photo")} onChange={onFile} />
    </div>
  );
}

function Required({ label }: { label: string }) {
  return (
    <>
      {label}
      <span className="ml-1 font-normal text-danger">필수</span>
    </>
  );
}
