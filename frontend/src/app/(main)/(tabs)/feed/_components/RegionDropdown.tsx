"use client";

import { ChevronDown, ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { findNode, regionLabel, REGION_TREE, type RegionNode } from "./regions";

interface RegionDropdownProps {
  /** 쉼표 구분 fullPath 목록. 예: "서울특별시 강남구,서울특별시 역삼1동" 또는 "" (전체) */
  value: string;
  onChange: (regions: string) => void;
}

/**
 * 추천 피드 지역 다중 선택 드롭다운.
 * - 시도 → 구/군 → 동 단계별 드릴다운
 * - 체크박스로 여러 지역 선택 후 "적용" 버튼 클릭 시 반영
 * - 각 단계 상단에 "○○ 전체" 체크박스 제공
 * - 뒤로 버튼으로 상위 단계 복귀 가능
 */
export function RegionDropdown({ value, onChange }: RegionDropdownProps) {
  const [open, setOpen] = useState(false);
  /** 화면에 보이는 단계 경로. 예: ["서울특별시"] → 강남구 목록 표시 중 */
  const [drillPath, setDrillPath] = useState<string[]>([]);
  /** 미확정 선택 목록 (적용 전 임시 상태) */
  const [draft, setDraft] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const selected = value ? value.split(",").filter(Boolean) : [];
  const isFiltered = selected.length > 0;

  // 열릴 때 현재 선택값으로 draft 초기화
  useEffect(() => {
    if (open) {
      setDraft(selected);
      setDrillPath([]);
      setQuery("");
      requestAnimationFrame(() => searchRef.current?.focus());
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // 바깥 클릭 닫기
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  // ESC 닫기
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  // 현재 드릴 경로의 하위 노드 목록
  const currentNodes: RegionNode[] =
    drillPath.length === 0 ? REGION_TREE : (findNode(drillPath)?.children ?? []);

  const filtered = currentNodes.filter((n) => n.name.includes(query));

  /** 체크박스 토글 */
  const toggle = (fullPath: string) => {
    setDraft((prev) =>
      prev.includes(fullPath) ? prev.filter((r) => r !== fullPath) : [...prev, fullPath],
    );
  };

  /** 드릴다운 진입 */
  const drill = (name: string) => {
    setDrillPath((p) => [...p, name]);
    setQuery("");
    requestAnimationFrame(() => searchRef.current?.focus());
  };

  /** 뒤로 한 단계 */
  const back = () => {
    setDrillPath((p) => p.slice(0, -1));
    setQuery("");
  };

  /** 적용 */
  const apply = () => {
    onChange(draft.join(","));
    setOpen(false);
  };

  /** 초기화 */
  const reset = () => setDraft([]);

  // 현재 드릴 경로 자체를 fullPath로 (예: "서울특별시 강남구")
  const drillFullPath = drillPath.join(" ");
  // 하위 단계에 진입했을 때 "○○ 전체" 체크박스의 label
  const allLabel = drillPath.length > 0
    ? `${drillPath[drillPath.length - 1]} 전체`
    : null;

  // 단계 타이틀
  const levelLabel = ["시/도 선택", "구/군 선택", "동 선택"][Math.min(drillPath.length, 2)];

  // 트리거 버튼 레이블
  const triggerLabel = (() => {
    if (!isFiltered) return "지역";
    if (selected.length === 1) return regionLabel(selected[0]);
    return `${regionLabel(selected[0])} 외 ${selected.length - 1}`;
  })();

  return (
    <div ref={containerRef} className="relative">
      {/* 트리거 버튼 */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          "flex h-7 items-center gap-1 rounded-full px-3 text-[12px] font-bold transition-colors",
          isFiltered ? "bg-primary text-white" : "bg-accent-soft text-primary-deep",
        ].join(" ")}
      >
        <span className="max-w-[80px] truncate">{triggerLabel}</span>
        {isFiltered ? (
          <X
            size={12}
            strokeWidth={2.5}
            className="-mr-0.5 shrink-0"
            onClick={(e) => { e.stopPropagation(); onChange(""); }}
          />
        ) : (
          <ChevronDown
            size={12}
            strokeWidth={2.5}
            className={["shrink-0 transition-transform", open ? "rotate-180" : ""].join(" ")}
          />
        )}
      </button>

      {/* 드롭다운 패널 */}
      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-52 overflow-hidden rounded-2xl bg-bg shadow-[0_4px_24px_rgba(0,0,0,0.12)] ring-1 ring-line">

          {/* 헤더 */}
          <div className="flex items-center gap-1 px-3 pt-3 pb-1">
            {drillPath.length > 0 && (
              <button type="button" onClick={back} className="inline-flex size-7 items-center justify-center rounded-full text-ink-sub active:bg-soft">
                <ChevronLeft size={16} strokeWidth={2} />
              </button>
            )}
            <span className="text-[12px] font-bold text-ink-sub">{levelLabel}</span>
          </div>

          {/* 검색 */}
          <div className="px-3 pb-2">
            <input
              ref={searchRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="검색"
              className="w-full rounded-lg bg-soft px-3 py-1.5 text-[13px] text-ink outline-none placeholder:text-ink-faint"
            />
          </div>

          {/* 목록 */}
          <ul className="max-h-52 overflow-y-auto overscroll-contain">
            {/* 하위 단계 진입 시 "○○ 전체" 체크 항목 */}
            {allLabel && !query && (
              <>
                <li>
                  <label className="flex cursor-pointer items-center gap-3 px-4 py-2.5">
                    <input
                      type="checkbox"
                      checked={draft.includes(drillFullPath)}
                      onChange={() => toggle(drillFullPath)}
                      className="size-4 accent-primary"
                    />
                    <span className={[
                      "text-[13px] font-bold",
                      draft.includes(drillFullPath) ? "text-primary-deep" : "text-ink",
                    ].join(" ")}>
                      {allLabel}
                    </span>
                  </label>
                </li>
                <li aria-hidden className="mx-4 border-t border-line" />
              </>
            )}

            {filtered.length === 0 ? (
              <li className="py-6 text-center text-[12px] text-ink-faint">검색 결과 없음</li>
            ) : (
              filtered.map((node) => {
                const nodePath = drillPath.length > 0
                  ? `${drillFullPath} ${node.name}`
                  : node.name;
                const isChecked = draft.includes(nodePath);
                // 하위 선택이 있으면 부모도 활성색으로 표시
                const hasChildSelected = draft.some((r) => r.startsWith(nodePath + " "));

                return (
                  <li key={node.name}>
                    <div className="flex items-center">
                      {/* 체크박스 + 이름 (클릭 시 토글) */}
                      <label className="flex flex-1 cursor-pointer items-center gap-3 px-4 py-2.5">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggle(nodePath)}
                          className="size-4 shrink-0 accent-primary"
                        />
                        <span className={[
                          "text-[13px]",
                          isChecked || hasChildSelected ? "font-bold text-primary-deep" : "text-ink",
                        ].join(" ")}>
                          {node.name}
                        </span>
                      </label>
                      {/* 하위 있으면 드릴 버튼 */}
                      {node.children && node.children.length > 0 && (
                        <button
                          type="button"
                          onClick={() => drill(node.name)}
                          className="flex h-full shrink-0 items-center pr-4 pl-1 py-2.5 text-ink-faint active:text-ink"
                          aria-label={`${node.name} 하위 지역 보기`}
                        >
                          <ChevronRight size={14} strokeWidth={2} />
                        </button>
                      )}
                    </div>
                  </li>
                );
              })
            )}
          </ul>

          {/* 하단 버튼 */}
          <div className="flex gap-2 border-t border-line px-3 py-3">
            <button
              type="button"
              onClick={reset}
              className="h-9 flex-1 rounded-full border border-line text-[13px] text-ink-sub active:bg-soft"
            >
              초기화
            </button>
            <button
              type="button"
              onClick={apply}
              className="h-9 flex-[2] rounded-full bg-primary text-[13px] font-bold text-white active:brightness-95 disabled:bg-soft disabled:text-ink-faint"
            >
              적용{draft.length > 0 ? ` (${draft.length})` : ""}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
