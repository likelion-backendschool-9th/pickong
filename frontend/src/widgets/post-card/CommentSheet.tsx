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
    if (replyTo) requestAnimationFrame(() => inputRef.current?.focus());
  }, [replyTo]);

  const roots = comments?.filter((c) => !c.parentId) ?? [];
  const repliesOf = (id: string) => comments?.filter((c) => c.parentId === id) ?? [];

  /** 답글 달기 버튼 클릭 */
  const handleReply = (comment: PostComment) => {
    setText(`@${comment.author.username} `);
    setReplyTo(comment);
  };

  /**
   * 색상 표시용 @username 감지. replyTo와 무관하게 텍스트에서 직접 추출.
   * - 답글 달기 버튼: replyTo 세팅 + @username 삽입 → 둘 다 활성
   * - 직접 타이핑: @username만 색상 표시, replyTo는 세팅 안 함 (일반 댓글로 전송)
   */
  const typedUsername = text.match(/^@(\S+)/)?.[1];
  const activeMention = (() => {
    if (replyTo && text.startsWith(`@${replyTo.author.username}`)) {
      return `@${replyTo.author.username}`;
    }
    if (!replyTo && typedUsername && comments?.some((c) => c.author.username === typedUsername)) {
      return `@${typedUsername}`;
    }
    return null;
  })();

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
              <span>{replyTo.author.username}님에게 답글 남기는 중</span>
              <button type="button" onClick={() => { setReplyTo(null); setText(""); }} className="font-bold">취소</button>
            </div>
          )}
          <div className="flex items-center gap-2">
            {/*
             * @username은 별도 span으로 색상 표시. input은 멘션 뒤 텍스트만 담당.
             * activeMention은 replyTo 여부와 무관하게 텍스트에서 직접 감지.
             */}
            <div className="flex h-11 flex-1 items-center overflow-hidden rounded-full bg-soft px-4 focus-within:ring-2 focus-within:ring-primary-deep/30">
              {activeMention && (
                <span className="shrink-0 text-[14px] font-medium text-primary-deep">
                  {activeMention}
                </span>
              )}
              <input
                ref={inputRef}
                value={activeMention ? text.slice(activeMention.length) : text}
                onChange={(e) => {
                  const suffix = e.target.value;
                  const val = activeMention ? activeMention + suffix : suffix;
                  setText(val);

                  // 답글 대상이 있는데 @username이 지워지면 답글 해제
                  if (replyTo && !val.startsWith(`@${replyTo.author.username}`)) {
                    setReplyTo(null);
                  }
                }}
                onKeyDown={(e) => {
                  // 멘션 뒤 텍스트가 비었을 때 backspace → 멘션 제거
                  const suffix = activeMention ? text.slice(activeMention.length) : text;
                  if (e.key === "Backspace" && activeMention && suffix === "") {
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
        <Avatar src={comment.author.profileImageUrl} name={comment.author.username} size={small ? 28 : 32} />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="text-[13px]">
          <span className="font-bold">{comment.author.username}</span>
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
