"use client";

import { useSearchParams } from "next/navigation";

/** Next의 Native History 연동을 이용해 검색·탭·필터를 URL에 보존합니다. */
export function useAdminSearch() {
  const params = useSearchParams();
  const setParam = (name: string, value: string, defaultValue = "") => {
    const url = new URL(window.location.href);
    if (value === defaultValue) url.searchParams.delete(name);
    else url.searchParams.set(name, value);
    window.history.replaceState(null, "", url.pathname + url.search + url.hash);
  };
  return { params, setParam };
}
