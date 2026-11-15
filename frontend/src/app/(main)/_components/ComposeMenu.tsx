"use client";

import { ChevronRight, ImagePlus, Link2, MapPinPlus } from "lucide-react";
import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";

/** 추가(+) 메뉴: 목적이 다른 세 가지 작업으로 분기합니다. */
const items = [
  { href: routes.newPost(), icon: ImagePlus, title: "게시물 올리기", description: "사진과 함께 장소를 공유해요" },
  { href: routes.newPlace(), icon: MapPinPlus, title: "장소 직접 추가", description: "검색하거나 지도에서 위치를 지정해요" },
  { href: routes.importLink(), icon: Link2, title: "AI 링크로 가져오기", description: "링크 속 장소를 확인하고 모아요" },
];

export function ComposeMenu({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="어떤 것을 추가할까요?">
      <ul className="-mx-2 pb-[calc(8px+env(safe-area-inset-bottom))]">
        {items.map((item) => (
          <li key={item.href}>
            <Link
              href={item.href}
              onClick={onClose}
              className="flex min-h-16 items-center gap-3.5 rounded-card px-2 py-3 active:bg-fill"
            >
              <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-soft text-primary-deep">
                <item.icon size={21} strokeWidth={1.7} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[15px] font-bold">{item.title}</span>
                <span className="block text-[12px] text-ink-sub">{item.description}</span>
              </span>
              <ChevronRight size={18} className="text-ink-faint" />
            </Link>
          </li>
        ))}
      </ul>
    </BottomSheet>
  );
}
