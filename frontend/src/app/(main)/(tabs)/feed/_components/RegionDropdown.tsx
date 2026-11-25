"use client";

import { ChevronDown, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { REGIONS } from "./regions";

interface RegionDropdownProps {
  value: string; // "전국" 또는 지역명
  onChange: (region: string) => void;
}

/**
 * 추천 피드 지역 필터 드롭다운.
 * 검색 + 선택 가능. 선택 후 자동으로 닫힘.
 */
export function RegionDropdown({ value, onChange }: RegionDropdownProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  // 열릴 때 검색창 포커스
  useEffect(() => {
    if (open) requestAnimationFrame(() => searchRef.current?.focus());
    else setQuery(""); // 닫힐 때 검색어 초기화
  }, [open]);

  // 바깥 클릭 시 닫기
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  // ESC 키 닫기
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const filtered = REGIONS.filter((r) => r.includes(query));
  const isFiltered = value !== "전국";

  const handleSelect = (region: string) => {
    onChange(region);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      {/* 트리거 버튼 */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          "flex h-7 items-center gap-1 rounded-full px-3 text-[12px] font-bold transition-colors",
          isFiltered
            ? "bg-primary text-white"
            : "bg-accent-soft text-primary-deep",
        ].join(" ")}
      >
        <span className="max-w-[64px] truncate">{value === "전국" ? "지역" : value.replace(/특별시|광역시|특별자치시|특별자치도|도$/, "")}</span>
        {isFiltered ? (
          <X
            size={12}
            strokeWidth={2.5}
            className="-mr-0.5"
            onClick={(e) => { e.stopPropagation(); handleSelect("전국"); }}
          />
        ) : (
          <ChevronDown size={12} strokeWidth={2.5} className={open ? "rotate-180 transition-transform" : "transition-transform"} />
        )}
      </button>

      {/* 드롭다운 패널 */}
      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-44 overflow-hidden rounded-2xl bg-bg shadow-[0_4px_24px_rgba(0,0,0,0.12)] ring-1 ring-line">
          {/* 검색 */}
          <div className="px-3 pt-3 pb-2">
            <input
              ref={searchRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="지역 검색"
              className="w-full rounded-lg bg-soft px-3 py-1.5 text-[13px] text-ink outline-none placeholder:text-ink-faint"
            />
          </div>
          {/* 목록 */}
          <ul className="max-h-52 overflow-y-auto overscroll-contain pb-2">
            {filtered.length === 0 ? (
              <li className="py-6 text-center text-[12px] text-ink-faint">검색 결과 없음</li>
            ) : (
              filtered.map((region) => (
                <li key={region}>
                  <button
                    type="button"
                    onClick={() => handleSelect(region)}
                    className={[
                      "w-full px-4 py-2.5 text-left text-[13px] transition-colors active:bg-soft",
                      region === value ? "font-bold text-primary-deep" : "text-ink",
                    ].join(" ")}
                  >
                    {region}
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
