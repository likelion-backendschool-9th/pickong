"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAddComment, useComments, type PostComment } from "@/entities/post";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Avatar, Skeleton } from "@/shared/ui/display";

/** 댓글 시트: 댓글 · 답글 보기와 작성 */
export function CommentSheet({ postId, open, onClose }: { postId: string; open: boolean; onClose: () => void }) {
  const { data: comments, isPending } = useComments(postId, open);
  const add = useAddComment(postId);
  const [text, setText] = useState("");
  const [replyTo, setReplyTo] = useState<PostComment | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  /** 답글 대상이 세팅된 경로: "button" = 답글 달기 버튼, "mention" = @닉네임 직접 입력 */
  const replySource = useRef<"button" | "mention">("button");

  // 시트가 열리면 input 포커스 (시트 슬라이드 업 애니메이션 220ms 뒤)
  useEffect(() => {
    if (!open) return;
    const id = setTimeout(() => inputRef.current?.focus(), 220);
    return () => clearTimeout(id);
  }, [open]);

  // 답글 달기 버튼으로 세팅된 경우에만 @닉네임 삽입 + 포커스
  // 직접 타이핑으로 감지된 경우(mention)에는 텍스트를 덮어쓰지 않음
  useEffect(() => {
    if (!replyTo || replySource.current === "mention") return;
    const mention = `@${replyTo.author.nickname} `;
    setText(mention);
    requestAnimationFrame(() => {
      const el = inputRef.current;
      if (!el) return;
      el.focus();
      el.setSelectionRange(mention.length, mention.length);
    });
  }, [replyTo]);

  const roots = comments?.filter((c) => !c.parentId) ?? [];
  const repliesOf = (id: string) => comments?.filter((c) => c.parentId === id) ?? [];

  /** 답글 달기 버튼 클릭 */
  const handleReply = (comment: PostComment) => {
    replySource.current = "button";
    setReplyTo(comment);
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    add.mutate(
      { content: text.trim(), parentId: replyTo?.parentId ?? replyTo?.id ?? null },
      { onSuccess: () => { setText(""); setReplyTo(null); } },
    );
  };

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={`댓글 ${comments?.length ?? ""}`}
      className="h-[80dvh]"
      footer={
        <form onSubmit={submit} className="space-y-2">
          {replyTo && (
            <div className="flex items-center justify-between text-[12px] text-ink-sub">
              <span>{replyTo.author.nickname}님에게 답글 남기는 중</span>
              <button type="button" onClick={() => { setReplyTo(null); setText(""); }} className="font-bold">취소</button>
            </div>
          )}
          <div className="flex items-center gap-2">
            {/*
             * @닉네임은 별도 span으로 분리해 색상 적용. input은 멘션 뒤 텍스트만 표시.
             * text 상태는 항상 전체 문자열(@닉네임 포함)로 유지하고, onChange에서 재조합합니다.
             */}
            <div className="flex h-11 flex-1 items-center overflow-hidden rounded-full bg-soft px-4 focus-within:ring-2 focus-within:ring-primary-deep/30">
              {replyTo && text.startsWith(`@${replyTo.author.nickname}`) && (
                <span className="shrink-0 text-[14px] font-medium text-primary-deep">
                  {`@${replyTo.author.nickname}`}
                </span>
              )}
              <input
                ref={inputRef}
                value={
                  replyTo && text.startsWith(`@${replyTo.author.nickname}`)
                    ? text.slice(`@${replyTo.author.nickname}`.length)
                    : text
                }
                onChange={(e) => {
                  const suffix = e.target.value;
                  const mention = replyTo ? `@${replyTo.author.nickname}` : null;
                  const val = mention && text.startsWith(mention) ? mention + suffix : suffix;
                  setText(val);

                  // "@닉네임 " 형태로 직접 입력하면 해당 댓글 작성자를 답글 대상으로 자동 감지
                  // (공백이 붙는 순간 확정 — 닉네임 완성 기준)
                  const mentionMatch = suffix.match(/^@(\S+)\s/);
                  if (mentionMatch && !replyTo) {
                    const nickname = mentionMatch[1];
                    const matched = comments?.find((c) => c.author.nickname === nickname);
                    if (matched) {
                      replySource.current = "mention";
                      setReplyTo(matched);
                    }
                  }
                }}
                onKeyDown={(e) => {
                  // input이 비었을 때 backspace → 멘션 span 제거
                  const mention = replyTo ? `@${replyTo.author.nickname}` : null;
                  const suffix = mention && text.startsWith(mention) ? text.slice(mention.length) : text;
                  if (e.key === "Backspace" && replyTo && suffix === "") {
                    setReplyTo(null);
                    setText("");
                  }
                }}
                placeholder={replyTo ? "답글 달기" : "댓글 달기"}
                aria-label="댓글 입력"
                className="min-w-0 flex-1 bg-transparent text-[14px] text-ink outline-none placeholder:text-ink-faint"
                maxLength={300}
              />
            </div>
            <button
              type="submit"
              disabled={!text.trim() || add.isPending}
              className="h-11 shrink-0 px-2 text-[14px] font-bold text-primary-deep disabled:text-ink-faint"
            >
              게시
            </button>
          </div>
          {add.isError && <p className="text-[12px] text-danger">{errorMessage(add.error)}</p>}
        </form>
      }
    >
      {isPending ? (
        <div className="space-y-4">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ) : roots.length === 0 ? (
        <p className="py-10 text-center text-[13px] text-ink-sub">첫 댓글을 남겨 보세요.</p>
      ) : (
        <ul className="space-y-5">
          {roots.map((c) => (
            <li key={c.id}>
              <CommentItem comment={c} onReply={() => handleReply(c)} />
              {repliesOf(c.id).length > 0 && (
                <ul className="mt-3 space-y-3 pl-11">
                  {repliesOf(c.id).map((r) => (
                    <li key={r.id}>
                      <CommentItem comment={r} onReply={() => handleReply(r)} small />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </BottomSheet>
  );
}

function CommentItem({ comment, onReply, small }: { comment: PostComment; onReply: () => void; small?: boolean }) {
  return (
    <div className="flex gap-3">
      <Link href={routes.user(comment.author.username)}>
        <Avatar src={comment.author.profileImageUrl} name={comment.author.nickname} size={small ? 28 : 32} />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="text-[13px]">
          <span className="font-bold">{comment.author.nickname}</span>
          <span className="ml-1.5 text-[11px] text-ink-faint">{formatRelative(comment.createdAt)}</span>
        </div>
        <p className="mt-0.5 text-[14px] leading-relaxed whitespace-pre-line">{comment.content}</p>
        <button type="button" onClick={onReply} className="mt-1 text-[12px] font-medium text-ink-sub">
          답글 달기
        </button>
      </div>
    </div>
  );
}
