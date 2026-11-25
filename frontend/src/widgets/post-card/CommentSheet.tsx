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

  // 시트가 열리면 input 포커스 (시트 슬라이드 업 애니메이션 220ms 뒤)
  useEffect(() => {
    if (!open) return;
    const id = setTimeout(() => inputRef.current?.focus(), 220);
    return () => clearTimeout(id);
  }, [open]);

  // 답글 달기 클릭 시 input 포커스
  useEffect(() => {
    if (replyTo) inputRef.current?.focus();
  }, [replyTo]);

  const roots = comments?.filter((c) => !c.parentId) ?? [];
  const repliesOf = (id: string) => comments?.filter((c) => c.parentId === id) ?? [];

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
              <button type="button" onClick={() => setReplyTo(null)} className="font-bold">취소</button>
            </div>
          )}
          <div className="flex items-center gap-2">
            <input
              ref={inputRef}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={replyTo ? "답글 달기" : "댓글 달기"}
              aria-label="댓글 입력"
              className="h-11 flex-1 rounded-full bg-soft px-4 text-[14px] outline-none placeholder:text-ink-faint focus:ring-2 focus:ring-primary-deep/30"
              maxLength={300}
            />
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
              <CommentItem comment={c} onReply={() => setReplyTo(c)} />
              {repliesOf(c.id).length > 0 && (
                <ul className="mt-3 space-y-3 pl-11">
                  {repliesOf(c.id).map((r) => (
                    <li key={r.id}>
                      <CommentItem comment={r} onReply={() => setReplyTo(r)} small />
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
