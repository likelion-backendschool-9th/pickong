"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  buildTimeline,
  insertAt,
  moveItem,
  removeItem,
  setStay,
  useTravelLegs,
  useUpdatePlanItems,
  type Plan,
  type PlanItem,
} from "@/entities/plan";
import { errorMessage } from "@/shared/api/errors";
import { useToast } from "@/shared/ui/Toast";

/**
 * 일정 편집 상태.
 * - 사용자가 바꾼 순서는 즉시 화면에 반영하고 서버에는 뒤에서 저장합니다.
 * - 이동 시간은 구간별로 계산하며, 계산 중인 구간은 추정치(임시값)로 시간표를 먼저 보여줍니다.
 * - 경고가 생겨도 사용자 순서를 되돌리지 않습니다.
 */
export function usePlanEditor(plan: Plan) {
  const [items, setItems] = useState<PlanItem[]>(plan.items);
  const version = useRef(0);
  const update = useUpdatePlanItems(plan.id);
  const toast = useToast();

  // 다른 화면(방문 기록 등)에서 서버 데이터가 바뀌면 동기화
  const serverKey = plan.items.map((i) => `${i.id}:${i.stayMinutes}:${i.visited}`).join("|");
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
          lastServerKey.current = saved.items.map((i) => `${i.id}:${i.stayMinutes}:${i.visited}`).join("|");
          setItems(saved.items);
        }
      },
      onError: (e) => {
        toast({ message: "저장하지 못했어요", detail: errorMessage(e) });
        setItems(plan.items);
      },
    });
  };

  const legs = useTravelLegs(plan, items);
  const timeline = useMemo(() => buildTimeline(plan, items, legs), [plan, items, legs]);
  const calculating = legs.some((l) => !l);

  return {
    items,
    timeline,
    calculating,
    saving: update.isPending,
    move: (from: number, to: number) => commit(moveItem(items, from, to)),
    remove: (id: string) => commit(removeItem(items, id)),
    setStay: (id: string, minutes: number) => commit(setStay(items, id, minutes)),
    insert: (index: number, item: PlanItem) => commit(insertAt(items, index, item)),
    replaceAll: (next: PlanItem[]) => commit(next),
  };
}
