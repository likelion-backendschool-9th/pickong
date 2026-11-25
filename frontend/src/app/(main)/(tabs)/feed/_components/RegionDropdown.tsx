"use client";

import { Check, ChevronDown, ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { findNode, regionLabel, REGION_TREE, type RegionNode } from "./regions";

interface RegionDropdownProps {
  /** 쉼표 구분 fullPath 목록. 예: "서울특별시 강남구,서울특별시 역삼동" 또는 "" (전체) */
  value: string;
  onChange: (regions: string) => void;
}

export function RegionDropdown({ value, onChange }: RegionDropdownProps) {
  const [open, setOpen] = useState(false);
  const [drillPath, setDrillPath] = useState<string[]>([]);
  const [draft, setDraft] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const selected = value ? value.split(",").filter(Boolean) : [];
  const isFiltered = selected.length > 0;

  useEffect(() => {
    if (open) {
      setDraft(selected);
      setDrillPath([]);
      setQuery("");
      requestAnimationFrame(() => searchRef.current?.focus());
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const currentNodes: RegionNode[] =
    drillPath.length === 0 ? REGION_TREE : (findNode(drillPath)?.children ?? []);
  const filtered = currentNodes.filter((n) => n.name.includes(query));

  const toggle = (fullPath: string) =>
    setDraft((prev) =>
      prev.includes(fullPath) ? prev.filter((r) => r !== fullPath) : [...prev, fullPath],
    );

  const drill = (name: string) => {
    setDrillPath((p) => [...p, name]);
    setQuery("");
    requestAnimationFrame(() => searchRef.current?.focus());
  };

  const back = () => { setDrillPath((p) => p.slice(0, -1)); setQuery(""); };
  const apply = () => { onChange(draft.join(",")); setOpen(false); };
  const reset = () => setDraft([]);

  const drillFullPath = drillPath.join(" ");
  const allLabel = drillPath.length > 0 ? `${drillPath[drillPath.length - 1]} 전체` : null;

  // 헤더: 드릴 경로를 › 구분으로 표시
  const breadcrumb = drillPath.length === 0
    ? "지역 선택"
    : drillPath.map((p, i) => {
        // 시도는 접미사 제거해서 짧게
        if (i === 0) return p.replace(/특별자치[시도]|특별[시도]|광역시|도$/, "").replace(/시$/, "");
        return p;
      }).join(" › ");

  const triggerLabel = !isFiltered
    ? "지역"
    : selected.length === 1
    ? regionLabel(selected[0])
    : `${regionLabel(selected[0])} 외 ${selected.length - 1}`;

  return (
    <div ref={containerRef} className="relative">
      {/* 트리거 */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          "flex h-7 items-center gap-1 rounded-full px-3 text-[12px] font-bold transition-colors",
          isFiltered ? "bg-primary-deep text-white" : "bg-accent-soft text-primary-deep",
        ].join(" ")}
      >
        <span className="max-w-[80px] truncate">{triggerLabel}</span>
        {isFiltered ? (
          <X size={12} strokeWidth={2.5} className="-mr-0.5 shrink-0"
            onClick={(e) => { e.stopPropagation(); onChange(""); }} />
        ) : (
          <ChevronDown size={12} strokeWidth={2.5}
            className={["shrink-0 transition-transform duration-200", open ? "rotate-180" : ""].join(" ")} />
        )}
      </button>

      {/* 패널 */}
      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-56 overflow-hidden rounded-2xl bg-bg shadow-[0_8px_32px_rgba(0,0,0,0.14)]">

          {/* 헤더 */}
          <div className="flex items-center gap-2 px-4 pt-4 pb-3">
            {drillPath.length > 0 && (
              <button type="button" onClick={back}
                className="inline-flex size-6 shrink-0 items-center justify-center rounded-full text-ink-sub active:bg-fill">
                <ChevronLeft size={15} strokeWidth={2.2} />
              </button>
            )}
            <span className="text-[13px] font-bold text-ink">{breadcrumb}</span>
          </div>

          {/* 검색 */}
          <div className="px-4 pb-3">
            <div className="flex items-center gap-2 rounded-xl bg-fill px-3 py-2 focus-within:ring-2 focus-within:ring-primary-deep/20">
              <input
                ref={searchRef}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="검색"
                className="flex-1 bg-transparent text-[13px] text-ink outline-none placeholder:text-ink-faint"
              />
              {query && (
                <button type="button" onClick={() => setQuery("")} className="text-ink-faint active:text-ink">
                  <X size={13} strokeWidth={2} />
                </button>
              )}
            </div>
          </div>

          {/* 목록 */}
          <ul className="max-h-[220px] overflow-y-auto overscroll-contain">
            {/* 하위 단계 "○○ 전체" */}
            {allLabel && !query && (
              <li>
                <button type="button" onClick={() => toggle(drillFullPath)}
                  className="flex w-full items-center gap-3 px-4 py-2.5 active:bg-fill">
                  <Checkbox checked={draft.includes(drillFullPath)} />
                  <span className={[
                    "flex-1 text-left text-[13px] font-semibold",
                    draft.includes(drillFullPath) ? "text-primary-deep" : "text-ink",
                  ].join(" ")}>
                    {allLabel}
                  </span>
                </button>
                {/* 구분선 */}
                <div className="mx-4 border-t border-line" />
              </li>
            )}

            {filtered.length === 0 ? (
              <li className="py-8 text-center text-[12px] text-ink-faint">검색 결과 없음</li>
            ) : filtered.map((node) => {
              const nodePath = drillPath.length > 0 ? `${drillFullPath} ${node.name}` : node.name;
              const isChecked = draft.includes(nodePath);
              const hasChildSelected = draft.some((r) => r.startsWith(nodePath + " "));

              return (
                <li key={node.name}>
                  <div className="flex items-stretch">
                    <button type="button" onClick={() => toggle(nodePath)}
                      className="flex flex-1 items-center gap-3 pl-4 pr-1 py-2.5 active:bg-fill">
                      <Checkbox checked={isChecked} />
                      <span className={[
                        "text-[13px]",
                        isChecked || hasChildSelected ? "font-semibold text-primary-deep" : "text-ink",
                      ].join(" ")}>
                        {node.name}
                      </span>
                    </button>
                    {node.children && node.children.length > 0 && (
                      <button type="button" onClick={() => drill(node.name)}
                        aria-label={`${node.name} 하위 지역 보기`}
                        className="flex items-center pl-3 pr-4 py-2.5 text-ink-faint active:text-ink-sub">
                        <ChevronRight size={14} strokeWidth={2} />
                      </button>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>

          {/* 하단 */}
          <div className="flex items-center gap-3 px-4 py-3 border-t border-line">
            <button type="button" onClick={reset}
              className="text-[12px] font-medium text-ink-sub active:text-ink">
              초기화
            </button>
            <button type="button" onClick={apply}
              className="ml-auto h-9 rounded-full bg-primary px-5 text-[13px] font-bold text-on-primary active:brightness-95">
              적용{draft.length > 0 ? ` ${draft.length}개` : ""}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** 커스텀 체크박스 */
function Checkbox({ checked }: { checked: boolean }) {
  return (
    <span className={[
      "inline-flex size-[18px] shrink-0 items-center justify-center rounded-[5px] border-2 transition-colors",
      checked ? "border-primary-deep bg-primary-deep" : "border-line bg-bg",
    ].join(" ")}>
      {checked && <Check size={11} strokeWidth={3} className="text-white" />}
    </span>
  );
}
