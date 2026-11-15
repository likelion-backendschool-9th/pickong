"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CollectionCard } from "@/entities/collection";
import { PlaceListItem } from "@/entities/place";
import { UserRow } from "@/entities/user";
import { FollowButton } from "@/features/follow-user";
import { routes } from "@/shared/config/routes";
import { Tabs } from "@/shared/ui/controls";
import { EmptyState, Spinner, Thumb } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useSearch, type SearchType } from "./_api/searchApi";

/** 통합 검색: 장소 / 사용자 / 게시물 / 컬렉션 → 각 상세 화면으로 연결 */
export function SearchScreen() {
  const router = useRouter();
  const [input, setInput] = useState("");
  const [q, setQ] = useState("");
  const [type, setType] = useState<SearchType>("place");
  const { data, isFetching } = useSearch(q, type);

  useEffect(() => {
    const t = setTimeout(() => setQ(input.trim()), 250);
    return () => clearTimeout(t);
  }, [input]);

  return (
    <div>
      <TopBar title="검색" backHref={routes.feed()} />
      <div className="px-5">
        <div className="relative">
          <Search size={18} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" />
          <Input autoFocus type="search" value={input} onChange={(e) => setInput(e.target.value)} placeholder="장소, 사용자, 게시물, 컬렉션 검색" aria-label="검색어" className="pl-10" />
          {isFetching && <Spinner className="absolute top-1/2 right-3.5 size-4 -translate-y-1/2" />}
        </div>
      </div>
      <div className="mt-2 border-b border-line px-5">
        <Tabs
          size="sm"
          items={[
            { value: "place", label: "장소" },
            { value: "user", label: "사용자" },
            { value: "post", label: "게시물" },
            { value: "collection", label: "컬렉션" },
          ]}
          value={type}
          onChange={setType}
        />
      </div>

      <div className="px-5 pb-8">
        {!q ? (
          <p className="py-10 text-center text-[13px] text-ink-sub">예: 성수, 카페, 지민, 서울숲</p>
        ) : data && data.type === type && data.items.length === 0 ? (
          <EmptyState title="검색 결과가 없어요" description="다른 검색어나 탭을 확인해 보세요." />
        ) : data?.type === "place" ? (
          <>
            <p className="pt-3 text-[13px] font-bold">장소 검색 결과</p>
            <ul className="divide-y divide-line">
              {data.items.map((p) => (
                <li key={p.id}>
                  <PlaceListItem place={p} onClick={() => router.push(routes.place(p.id))} />
                </li>
              ))}
            </ul>
          </>
        ) : data?.type === "user" ? (
          <ul className="pt-2">
            {data.items.map((u) => (
              <li key={u.id}>
                <UserRow user={u} trailing={<FollowButton user={u} />} />
              </li>
            ))}
          </ul>
        ) : data?.type === "post" ? (
          <ul className="divide-y divide-line">
            {data.items.map((p) => (
              <li key={p.id}>
                <button type="button" onClick={() => router.push(routes.post(p.id))} className="flex w-full items-center gap-3 py-3 text-left">
                  <Thumb src={p.photos[0].url} alt="" className="size-16" />
                  <div className="min-w-0">
                    <p className="line-clamp-2 text-[14px] font-bold">{p.content.split("\n")[0]}</p>
                    <p className="mt-0.5 text-[12px] text-ink-sub">
                      {p.author.nickname} · 사진 {p.photos.length} · 장소 {p.places.length}곳
                    </p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        ) : data?.type === "collection" ? (
          <div className="divide-y divide-line">
            {data.items.map((c) => (
              <CollectionCard key={c.id} collection={c} />
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}
