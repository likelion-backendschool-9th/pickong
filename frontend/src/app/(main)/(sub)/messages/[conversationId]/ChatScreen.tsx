"use client";

import { ArrowUp, ImagePlus, MapPin, Plus, X } from "lucide-react";
import Link from "next/link";
import { Fragment, useEffect, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent } from "react";
import { useConversation, useMessages, useSendMessage, type Message } from "@/entities/conversation";
import { categoryLabel, useSavedPlaces, type Place } from "@/entities/place";
import { PlaceSearchPanel } from "@/features/place-search";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatClock, formatLongDate, localYmd } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Avatar, ErrorState, Photo, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";

/** DM 대화방: 날짜별로 묶은 말풍선 + 게시물 · 장소 공유 카드 + 하단 입력창 */
export function ChatScreen({ id }: { id: string }) {
  const { data: conversation, isError: convError, error: convErr } = useConversation(id);
  const { data: messages, isPending, isError, error, refetch } = useMessages(id);
  const bottomRef = useRef<HTMLDivElement>(null);
  const count = messages?.length ?? 0;

  // 처음 열 때와 새 메시지가 올 때 맨 아래로
  useEffect(() => {
    if (count) bottomRef.current?.scrollIntoView({ block: "end" });
  }, [count]);

  const partner = conversation?.partner;

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar
        backHref={routes.messages()}
        title={
          partner ? (
            <Link href={routes.user(partner.username)} className="inline-flex items-center gap-2">
              <Avatar src={partner.profileImageUrl} name={partner.nickname} size={28} />
              {partner.nickname}
            </Link>
          ) : (
            "메시지"
          )
        }
        className="border-b border-line"
      />

      <div className="flex-1 px-4 py-3" role="log" aria-label="메시지" aria-live="polite">
        {convError ? (
          <ErrorState message={errorMessage(convErr)} />
        ) : isPending ? (
          <div className="space-y-3">
            <Skeleton className="h-10 w-2/3" />
            <Skeleton className="ml-auto h-10 w-1/2" />
          </div>
        ) : isError ? (
          <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
        ) : messages.length === 0 ? (
          <p className="pt-10 text-center text-[13px] text-ink-faint">{partner ? `${partner.nickname}님에게 첫 메시지를 보내 보세요.` : ""}</p>
        ) : (
          <ol className="space-y-1.5">
            {messages.map((m, i) => {
              const prev = messages[i - 1];
              const next = messages[i + 1];
              const newDay = !prev || localYmd(prev.createdAt) !== localYmd(m.createdAt);
              // 같은 사람이 같은 분에 보낸 연속 메시지는 마지막에만 시각 표시
              const showTime = !next || next.isMine !== m.isMine || formatClock(next.createdAt) !== formatClock(m.createdAt);
              const showAvatar = !m.isMine && (!prev || prev.isMine || newDay);
              return (
                <Fragment key={m.id}>
                  {newDay && (
                    <li className="py-3 text-center text-[12px] text-ink-faint" aria-label={formatLongDate(localYmd(m.createdAt))}>
                      {formatLongDate(localYmd(m.createdAt))}
                    </li>
                  )}
                  <MessageItem message={m} showTime={showTime} avatar={showAvatar ? partner : undefined} />
                </Fragment>
              );
            })}
          </ol>
        )}
        <div ref={bottomRef} />
      </div>

      <Composer id={id} disabled={!conversation} />
    </div>
  );
}

function MessageItem({
  message: m,
  showTime,
  avatar,
}: {
  message: Message;
  showTime: boolean;
  avatar?: { nickname: string; profileImageUrl: string | null };
}) {
  return (
    <li className={cn("flex items-end gap-1.5", m.isMine ? "flex-row-reverse" : "flex-row")}>
      {!m.isMine && <span className="w-7 shrink-0">{avatar && <Avatar src={avatar.profileImageUrl} name={avatar.nickname} size={28} />}</span>}
      <div className={cn("flex max-w-[75%] flex-col gap-1", m.isMine ? "items-end" : "items-start")}>
        {m.attachment && <AttachmentCard attachment={m.attachment} />}
        {m.text && (
          <p
            className={cn(
              "rounded-[18px] px-3.5 py-2 text-[14px] leading-snug break-words whitespace-pre-wrap",
              m.isMine ? "rounded-br-md bg-primary text-on-primary" : "rounded-bl-md bg-fill text-ink",
            )}
          >
            {m.text}
          </p>
        )}
      </div>
      {showTime && <time className="shrink-0 pb-0.5 text-[10px] text-ink-faint" dateTime={m.createdAt}>{formatClock(m.createdAt)}</time>}
    </li>
  );
}

function AttachmentCard({ attachment }: { attachment: NonNullable<Message["attachment"]> }) {
  const box = "block w-56 overflow-hidden rounded-card border border-line bg-bg active:bg-fill";
  if (attachment.type === "IMAGE") {
    return (
      <a href={attachment.url} target="_blank" rel="noreferrer" className="relative block aspect-[4/3] w-56 overflow-hidden rounded-card bg-fill" aria-label="보낸 사진 크게 보기">
        <Photo src={attachment.url} alt="보낸 사진" sizes="224px" />
      </a>
    );
  }
  if (attachment.type === "POST") {
    const post = attachment.post;
    if (!post) return <p className={cn(box, "px-3 py-2.5 text-[12px] text-ink-faint")}>볼 수 없는 게시물이에요</p>;
    return (
      <Link href={routes.post(post.id)} className={box}>
        {post.imageUrl && <Thumb src={post.imageUrl} alt="" className="aspect-[4/3] w-full rounded-none" />}
        <span className="block px-3 py-2">
          <span className="block text-[12px] font-bold">{post.authorNickname}님의 게시물</span>
          <span className="block truncate text-[12px] text-ink-sub">{post.content.split("\n")[0]}</span>
        </span>
      </Link>
    );
  }
  const place = attachment.place;
  if (!place) return <p className={cn(box, "px-3 py-2.5 text-[12px] text-ink-faint")}>찾을 수 없는 장소예요</p>;
  return (
    <Link href={routes.place(place.id)} className={cn(box, "flex items-center gap-2.5 p-2.5")}>
      <Thumb src={place.imageUrl} alt="" className="size-12" />
      <span className="min-w-0">
        <span className="block truncate text-[13px] font-bold">{place.name}</span>
        <span className="block text-[12px] text-ink-sub">
          {place.region} · {categoryLabel(place.category)}
        </span>
      </span>
    </Link>
  );
}

/** 보내기 전 첨부 (사진은 브라우저 임시 URL: 업로드 API 연결 전) */
type Draft = { type: "IMAGE"; url: string } | { type: "PLACE"; place: Place };

const MAX_IMAGE_MB = 10;

/**
 * 입력창: 왼쪽 + 버튼으로 사진 · 장소를 첨부하고, Enter로 보내고 Shift+Enter로 줄바꿈 (한글 조합 중 Enter는 무시).
 * 글 없이 첨부만 보낼 수도 있습니다.
 */
function Composer({ id, disabled }: { id: string; disabled: boolean }) {
  const [text, setText] = useState("");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [sheet, setSheet] = useState<"menu" | "place" | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const send = useSendMessage(id);
  const toast = useToast();
  const { data: saved = [] } = useSavedPlaces();
  const canSend = !disabled && (text.trim().length > 0 || !!draft) && !send.isPending;

  const clearDraft = () => {
    if (draft?.type === "IMAGE") URL.revokeObjectURL(draft.url);
    setDraft(null);
  };

  const pickImage = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!file.type.startsWith("image/")) return toast({ message: "사진 파일만 보낼 수 있어요" });
    if (file.size > MAX_IMAGE_MB * 1024 * 1024) return toast({ message: `사진은 ${MAX_IMAGE_MB}MB까지 보낼 수 있어요` });
    clearDraft();
    setDraft({ type: "IMAGE", url: URL.createObjectURL(file) });
  };

  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    if (!canSend) return;
    const attachment = draft ? (draft.type === "IMAGE" ? draft : { type: "PLACE" as const, placeId: draft.place.id }) : null;
    send.mutate(
      { text: text.trim(), attachment },
      {
        onSuccess: () => {
          setText("");
          setDraft(null); // 보낸 사진 URL은 말풍선에서 계속 쓰므로 해제하지 않습니다.
        },
        onError: (err) => toast({ message: errorMessage(err) }),
      },
    );
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className="sticky bottom-0 z-20 border-t border-line bg-bg pb-[env(safe-area-inset-bottom)]">
      {draft && (
        <div className="flex items-center gap-2.5 px-3 pt-2">
          {draft.type === "IMAGE" ? (
            <span className="relative size-14 shrink-0 overflow-hidden rounded-lg bg-fill">
              <Photo src={draft.url} alt="첨부할 사진" sizes="56px" />
            </span>
          ) : (
            <Thumb src={draft.place.imageUrl} alt="" className="size-14" />
          )}
          <span className="min-w-0 flex-1">
            <span className="block text-[12px] text-ink-faint">{draft.type === "IMAGE" ? "사진 첨부" : "장소 첨부"}</span>
            {draft.type === "PLACE" && (
              <span className="block truncate text-[14px] font-bold">
                {draft.place.name} <span className="font-normal text-ink-sub">· {draft.place.region}</span>
              </span>
            )}
          </span>
          <button
            type="button"
            onClick={clearDraft}
            aria-label="첨부 빼기"
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-full text-ink-sub active:bg-fill"
          >
            <X size={18} />
          </button>
        </div>
      )}
      <form onSubmit={submit} className="flex items-end gap-2 px-3 py-2">
        <button
          type="button"
          onClick={() => setSheet("menu")}
          disabled={disabled}
          aria-label="사진 · 장소 첨부"
          aria-haspopup="dialog"
          className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-fill text-ink active:bg-line disabled:text-ink-faint"
        >
          <Plus size={22} strokeWidth={2} />
        </button>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          rows={1}
          maxLength={1000}
          placeholder="메시지 보내기"
          aria-label="메시지 입력"
          className="field-sizing-content max-h-32 min-h-10 flex-1 resize-none rounded-[20px] bg-fill px-4 py-2.5 text-[14px] leading-snug outline-none placeholder:text-ink-faint"
        />
        <button
          type="submit"
          disabled={!canSend}
          aria-label="보내기"
          className="inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary text-on-primary disabled:bg-fill disabled:text-ink-faint"
        >
          <ArrowUp size={20} strokeWidth={2.4} />
        </button>
      </form>

      <input ref={fileRef} type="file" accept="image/*" onChange={pickImage} className="hidden" aria-hidden tabIndex={-1} />

      <BottomSheet open={sheet === "menu"} onClose={() => setSheet(null)} title="첨부">
        <div className="grid grid-cols-2 gap-3 pb-2">
          <button
            type="button"
            onClick={() => {
              setSheet(null);
              fileRef.current?.click();
            }}
            className="flex flex-col items-center gap-2 rounded-card bg-soft py-5 text-[14px] font-bold text-ink active:bg-selected"
          >
            <ImagePlus size={26} className="text-primary-deep" />
            사진
          </button>
          <button
            type="button"
            onClick={() => setSheet("place")}
            className="flex flex-col items-center gap-2 rounded-card bg-soft py-5 text-[14px] font-bold text-ink active:bg-selected"
          >
            <MapPin size={26} className="text-primary-deep" />
            장소
          </button>
        </div>
      </BottomSheet>

      <BottomSheet open={sheet === "place"} onClose={() => setSheet(null)} title="보낼 장소" className="h-[85dvh]">
        {sheet === "place" && (
          <PlaceSearchPanel
            autoFocus
            recentTitle="내가 저장한 장소"
            recent={saved.map((s) => ({ place: s.place, note: `${s.place.region} · ${categoryLabel(s.place.category)}` }))}
            onSelect={(place) => {
              clearDraft();
              setDraft({ type: "PLACE", place });
              setSheet(null);
            }}
          />
        )}
      </BottomSheet>
    </div>
  );
}
