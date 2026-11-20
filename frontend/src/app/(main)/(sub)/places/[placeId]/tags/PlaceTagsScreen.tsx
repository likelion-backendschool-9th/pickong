"use client";

import { Hash, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { usePlaceDetail } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { usePlaceTags, useSavePlaceTags } from "../_api/myRecordApi";
import { addTag, TAGS_PER_PLACE } from "./_lib/tags";

export function PlaceTagsScreen({ placeId }: { placeId: string }) {
  const { data: detail } = usePlaceDetail(placeId);
  const { data, isPending, isError, error } = usePlaceTags(placeId);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  return <TagEditor placeId={placeId} placeName={detail?.place.name} saved={!!detail?.saved} initial={data} />;
}

function TagEditor({ placeId, placeName, saved, initial }: { placeId: string; placeName?: string; saved: boolean; initial: string[] }) {
  const router = useRouter();
  const toast = useToast();
  const save = useSavePlaceTags(placeId);
  const [tags, setTags] = useState(initial);
  const [input, setInput] = useState("");
  const [inputError, setInputError] = useState<string | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next = addTag(tags, input);
    setTags(next.tags);
    setInputError(next.error);
    if (!next.error) setInput("");
  };

  return (
    <Screen>
      <TopBar title={placeName ? `${placeName} · 내 태그` : "내 태그"} backHref={routes.place(placeId)} />
      <div className="px-5 pt-1 pb-6">
        <p className="text-[13px] text-ink-sub">나만 보는 분류예요. ‘조용한’, ‘비오는날’처럼 나중에 찾기 쉬운 말로 달아 두세요.</p>
        {!saved && (
          <Notice tone="warning" className="mt-3">
            찜콩한 장소에만 태그를 달 수 있어요.
          </Notice>
        )}
        <form onSubmit={submit} className="mt-4 flex gap-2">
          <div className="relative flex-1">
            <Hash size={16} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-faint" aria-hidden />
            <Input
              aria-label="새 태그"
              value={input}
              onChange={(e) => {
                setInput(e.target.value);
                setInputError(null);
              }}
              placeholder="태그 입력 후 추가"
              className="pl-9"
              enterKeyHint="done"
            />
          </div>
          <Button type="submit" variant="secondary" size="lg" disabled={!input.trim() || !saved}>
            추가
          </Button>
        </form>
        {inputError && (
          <p className="mt-2 text-[12px] text-danger" role="alert">
            {inputError}
          </p>
        )}

        <p className="mt-6 mb-2 text-[13px] font-bold">
          단 태그 <span className="font-normal text-ink-faint">{tags.length}/{TAGS_PER_PLACE}</span>
        </p>
        {tags.length === 0 ? (
          <p className="text-[13px] text-ink-faint">아직 단 태그가 없어요.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {tags.map((t) => (
              <li key={t}>
                <span className="inline-flex h-8 items-center gap-1 rounded-full border border-primary-deep/25 bg-selected pr-1 pl-3 text-[13px] font-bold text-primary-deep">
                  #{t}
                  <button
                    type="button"
                    onClick={() => setTags((s) => s.filter((x) => x !== t))}
                    aria-label={`${t} 태그 빼기`}
                    className="inline-flex size-6 items-center justify-center rounded-full active:bg-primary-deep/10"
                  >
                    <X size={14} />
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
        {save.isError && <Notice tone="danger" className="mt-4">{errorMessage(save.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!saved}
          loading={save.isPending}
          onClick={() =>
            save.mutate(tags, {
              onSuccess: () => {
                toast({ message: "태그를 저장했어요" });
                router.replace(routes.place(placeId));
              },
            })
          }
        >
          저장
        </Button>
      </BottomCta>
    </Screen>
  );
}
