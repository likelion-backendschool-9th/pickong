import { useEffect, useState } from "react";

/** 값이 바뀐 뒤 delay(ms) 동안 더 바뀌지 않으면 그 값을 돌려줍니다 (검색 요청 줄이기). */
export function useDebounced<T>(value: T, delay = 250) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}
