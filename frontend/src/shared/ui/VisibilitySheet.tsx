"use client";

import { useState } from "react";
import { VISIBILITY_OPTIONS, type Visibility } from "@/shared/config/visibility";
import { BottomSheet } from "./BottomSheet";
import { Button } from "./Button";
import { SelectRow } from "./controls";

interface VisibilitySheetProps {
  open: boolean;
  value: Visibility;
  onClose: () => void;
  onChange: (value: Visibility) => void;
  title?: string;
}

/** 공개 범위 선택: 전체 공개 / 팔로워 공개 / 비공개 */
export function VisibilitySheet({ open, value, onClose, onChange, title = "누가 볼 수 있나요?" }: VisibilitySheetProps) {
  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={title}
      footer={null}
    >
      {open && <VisibilityOptions initial={value} onDone={(v) => { onChange(v); onClose(); }} />}
    </BottomSheet>
  );
}

function VisibilityOptions({ initial, onDone }: { initial: Visibility; onDone: (v: Visibility) => void }) {
  const [selected, setSelected] = useState(initial);
  return (
    <div className="space-y-1">
      {VISIBILITY_OPTIONS.map((opt) => (
        <SelectRow
          key={opt.value}
          type="radio"
          checked={selected === opt.value}
          onToggle={() => setSelected(opt.value)}
          title={opt.label}
          description={opt.description}
          highlight
        />
      ))}
      <Button block className="mt-4" onClick={() => onDone(selected)}>
        선택 완료
      </Button>
    </div>
  );
}
