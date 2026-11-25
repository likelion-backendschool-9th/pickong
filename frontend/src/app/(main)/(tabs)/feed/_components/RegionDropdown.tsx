"use client";

import { ChevronDown, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { findNode, regionLabel, REGION_TREE, type RegionNode } from "./regions";

interface RegionDropdownProps {
  /** URL에 저장되는 fullPath. 예: "서울특별시 강남구 역삼1동" 또는 "전국" */
  value: string;
  onChange: (region: string) => void;
}

/**
 * 추천 피드 지역 필터 드롭다운 (드릴다운 방식).
 * - 시도 → 구/군 → 동 순서로 단계별 선택
 * - 각 단계에서 "전체" 선택 시 상위 단계까지만 필터링
 * - 선택 중 아무 단계에서나 "전체"로 현재 단계를 커버하는 범위 선택 가능
 */
export function RegionDropdown({ value, onChange }: RegionDropdownProps) {
  const [open, setOpen] = useState(false);
  /** 드릴다운 단계 경로. 예: ["서울특별시", "강남구"] */
  const [drillPath, setDrillPath] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const isFiltered = value !== "전국";

  // 열릴 때 현재 선택값 기준으로 드릴 경로 초기화 + 검색창 포커스
  useEffect(() => {
    if (open) {
      if (isFiltered) {
        const parts = value.split(" ");
        // 마지막이 동이면 구까지만 열어둠(동 목록 보여줌), 구면 시까지
        setDrillPath(parts.length > 1 ? parts.slice(0, -1) : []);
      } else {
        setDrillPath([]);
      }
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
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  // 현재 드릴 경로의 노드 목록
  const currentNodes: RegionNode[] = drillPath.length === 0
    ? REGION_TREE
    : (findNode(drillPath)?.children ?? []);

  const filtered = currentNodes.filter((n) => n.name.includes(query));

  /** 항목 선택: 하위 없으면 바로 확정, 있으면 드릴다운 */
  const handleSelect = (node: RegionNode) => {
    const newPath = [...drillPath, node.name];
    if (!node.children || node.children.length === 0) {
      // 최하위 → 선택 확정
      onChange(newPath.join(" "));
      setOpen(false);
    } else {
      // 하위 있음 → 한 단계 내려감
      setDrillPath(newPath);
      setQuery("");
      requestAnimationFrame(() => searchRef.current?.focus());
    }
  };

  /** "○○ 전체" 선택: 현재 드릴 경로를 그대로 region 값으로 사용 */
  const handleSelectAll = () => {
    if (drillPath.length === 0) {
      onChange("전국");
    } else {
      onChange(drillPath.join(" "));
    }
    setOpen(false);
  };

  /** 뒤로 한 단계 */
  const handleBack = () => {
    setDrillPath((p) => p.slice(0, -1));
    setQuery("");
  };

  // 패널 상단 타이틀: "시/도 선택", "구/군 선택", "동 선택"
  const levelLabel = ["시/도 선택", "구/군 선택", "동 선택"][Math.min(drillPath.length, 2)];
  // "전체" 버튼 텍스트
  const allLabel = drillPath.length === 0
    ? "전국"
    : `${drillPath[drillPath.length - 1]} 전체`;

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
        <span className="max-w-[72px] truncate">
          {isFiltered ? regionLabel(value) : "지역"}
        </span>
        {isFiltered ? (
          <X
            size={12}
            strokeWidth={2.5}
            className="-mr-0.5"
            onClick={(e) => {
              e.stopPropagation();
              onChange("전국");
            }}
          />
        ) : (
          <ChevronDown
            size={12}
            strokeWidth={2.5}
            className={open ? "rotate-180 transition-transform" : "transition-transform"}
          />
        )}
      </button>

      {/* 드롭다운 패널 */}
      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-48 overflow-hidden rounded-2xl bg-bg shadow-[0_4px_24px_rgba(0,0,0,0.12)] ring-1 ring-line">
          {/* 헤더: 뒤로가기 + 타이틀 */}
          <div className="flex items-center gap-2 px-3 pt-3 pb-1">
            {drillPath.length > 0 && (
              <button
                type="button"
                onClick={handleBack}
                className="text-[12px] font-medium text-ink-sub active:text-ink"
              >
                ←
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
          <ul className="max-h-56 overflow-y-auto overscroll-contain pb-2">
            {/* 전체 선택 옵션 (검색어 없을 때만 표시) */}
            {!query && (
              <li>
                <button
                  type="button"
                  onClick={handleSelectAll}
                  className={[
                    "w-full px-4 py-2.5 text-left text-[13px] font-bold transition-colors active:bg-soft",
                    (drillPath.length === 0 && value === "전국") ||
                    (drillPath.length > 0 && value === drillPath.join(" "))
                      ? "text-primary-deep"
                      : "text-ink",
                  ].join(" ")}
                >
                  {allLabel}
                </button>
              </li>
            )}

            {filtered.length === 0 ? (
              <li className="py-4 text-center text-[12px] text-ink-faint">검색 결과 없음</li>
            ) : (
              filtered.map((node) => {
                const nodePath = [...drillPath, node.name].join(" ");
                const isSelected = value === nodePath || value.startsWith(nodePath + " ");
                return (
                  <li key={node.name}>
                    <button
                      type="button"
                      onClick={() => handleSelect(node)}
                      className={[
                        "flex w-full items-center px-4 py-2.5 text-left text-[13px] transition-colors active:bg-soft",
                        isSelected ? "font-bold text-primary-deep" : "text-ink",
                      ].join(" ")}
                    >
                      <span className="flex-1">{node.name}</span>
                      {/* 하위 있으면 chevron 표시 */}
                      {node.children && node.children.length > 0 && (
                        <ChevronRight size={14} strokeWidth={2} className="shrink-0 text-ink-faint" />
                      )}
                    </button>
                  </li>
                );
              })
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
