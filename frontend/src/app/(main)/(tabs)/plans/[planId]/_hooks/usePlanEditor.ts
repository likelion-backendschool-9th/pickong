"use client";

import { useEffect, useRef, useState } from "react";
import {
  appendItem,
  moveItem,
  removeItem,
  setFixedTime,
  useUpdatePlanItems,
  type Plan,
  type PlanItem,
} from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { useToast } from "@/shared/ui/Toast";

const keyOf = (items: PlanItem[]) => items.map((i) => `${i.id}:${i.fixedTime ?? ""}:${i.visited}`).join("|");

/**
 * 일정 편집 상태: 바꾼 순서 · 고정 시간은 즉시 화면에 반영하고 서버에는 뒤에서 저장합니다.
 */
export function usePlanEditor(plan: Plan) {
  const [items, setItems] = useState<PlanItem[]>(plan.items);
  const version = useRef(0);
  const update = useUpdatePlanItems(plan.id);
  const toast = useToast();

  // 다른 화면(방문 기록 등)에서 서버 데이터가 바뀌면 동기화
  const serverKey = keyOf(plan.items);
  const lastServerKey = useRef(serverKey);
  useEffect(() => {
    if (serverKey !== lastServerKey.current && !update.isPending) {
      lastServerKey.current = serverKey;
      setItems(plan.items);
    }
  }, [serverKey, plan.items, update.isPending]);

  const commit = (next: PlanItem[]) => {
    if (next === items) return;
    const v = ++version.current;
    setItems(next);
    update.mutate(next, {
      onSuccess: (saved) => {
        // 저장 중 추가 변경이 없을 때만 서버 id로 교체
        if (v === version.current) {
          lastServerKey.current = keyOf(saved.items);
          setItems(saved.items);
        }
      },
      onError: (e) => {
        toast({ message: "저장하지 못했어요", detail: errorMessage(e) });
        setItems(plan.items);
      },
    });
  };

  return {
    items,
    saving: update.isPending,
    move: (from: number, to: number) => commit(moveItem(items, from, to)),
    remove: (id: string) => commit(removeItem(items, id)),
    setFixedTime: (id: string, time: string | null) => commit(setFixedTime(items, id, time)),
    add: (item: PlanItem) => commit(appendItem(items, item)),
  };
}
