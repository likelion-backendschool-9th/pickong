"use client";

import Link from "next/link";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useAddComment, useComments, useDeleteComment, useUpdateComment, type PostComment } from "@/entities/post";
import { useMe } from "@/entities/user";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Avatar, Skeleton } from "@/shared/ui/display";

/** 댓글 시트: 댓글 · 답글 보기와 작성 */
export function CommentSheet({ postId, open, onClose }: { postId: string; open: boolean; onClose: () => void }) {
  const { data: comments, isPending } = useComments(postId, open);
  const { data: me } = useMe();
  const add = useAddComment(postId);
  const [text, setText] = useState("");
  const [replyTo, setReplyTo] = useState<PostComment | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 시트가 열리면 슬라이드 업 애니메이션(220ms) 뒤 포커스
  useEffect(() => {
    if (!open) return;
    const id = setTimeout(() => inputRef.current?.focus(), 220);
    return () => clearTimeout(id);
  }, [open]);

  // 답글 대상이 세팅되면 포커스
  useEffect(() => {
    if (replyTo) requestAnimationFrame(() => inputRef.current?.focus());
  }, [replyTo]);

  const roots = comments?.filter((c) => !c.parentId) ?? [];
  const repliesOf = (id: string) => comments?.filter((c) => c.parentId === id) ?? [];

  /** 답글 달기 클릭: @username 삽입 + 답글 대상 세팅 */
  const handleReply = (comment: PostComment) => {
    setText(`@${comment.author.username} `);
    setReplyTo(comment);
  };

  const handleChange = (val: string) => {
    setText(val);
    // @username이 지워지면 답글 대상 해제
    if (replyTo && !val.startsWith(`@${replyTo.author.username}`)) {
      setReplyTo(null);
    }
  };

  /** 취소: 답글 대상 해제 + 입력창 초기화 */
  const handleCancel = () => {
    setReplyTo(null);
    setText("");
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
              <span>{replyTo.author.username}님에게 답글 남기는 중</span>
              <button type="button" onClick={handleCancel} className="font-bold">취소</button>
            </div>
          )}
          <div className="flex items-center gap-2">
            <input
              ref={inputRef}
              value={text}
              onChange={(e) => handleChange(e.target.value)}
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
              <CommentItem
                postId={postId}
                comment={c}
                isMine={me?.id === c.author.id}
                onReply={() => handleReply(c)}
                onDelete={() => { if (replyTo?.id === c.id) setReplyTo(null); }}
              />
              {repliesOf(c.id).length > 0 && (
                <ul className="mt-3 space-y-3 pl-11">
                  {repliesOf(c.id).map((r) => (
                    <li key={r.id}>
                      <CommentItem
                        postId={postId}
                        comment={r}
                        isMine={me?.id === r.author.id}
                        onReply={() => handleReply(r)}
                        onDelete={() => { if (replyTo?.id === r.id) setReplyTo(null); }}
                        small
                      />
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

/**
 * 댓글 본문에서 @username 패턴을 찾아 프로필 링크로 변환.
 * 예) "@민수 반가워요!" → <Link href="/users/민수">@민수</Link> 반가워요!
 */
function renderContent(content: string): ReactNode {
  const parts = content.split(/(@\S+)/);
  return parts.map((part, i) => {
    if (/^@\S+$/.test(part)) {
      const username = part.slice(1);
      return (
        <Link key={i} href={routes.user(username)} className="font-semibold text-primary-deep">
          {part}
        </Link>
      );
    }
    return part;
  });
}

function CommentItem({
  postId,
  comment,
  isMine,
  onReply,
  onDelete,
  small,
}: {
  postId: string;
  comment: PostComment;
  isMine: boolean;
  onReply: () => void;
  onDelete: () => void;
  small?: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState(comment.content ?? "");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const editRef = useRef<HTMLTextAreaElement>(null);
  const confirmTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const update = useUpdateComment(postId);
  const remove = useDeleteComment(postId);

  // 수정 모드 시작 시 textarea에 포커스
  useEffect(() => {
    if (editing) requestAnimationFrame(() => editRef.current?.focus());
  }, [editing]);

  // 삭제 확인 타이머 정리
  useEffect(() => () => { if (confirmTimerRef.current) clearTimeout(confirmTimerRef.current); }, []);

  const handleEditSave = () => {
    if (!editText.trim() || editText.trim() === comment.content) {
      setEditing(false);
      return;
    }
    update.mutate(
      { commentId: comment.id, content: editText.trim() },
      {
        onSuccess: () => setEditing(false),
        onError: (error: unknown) => {
          // 이미 삭제된 댓글이면 편집 모드 종료
          if (error instanceof ApiError && error.isNotFound) setEditing(false);
        },
      },
    );
  };

  // 첫 클릭: 확인 상태로 전환 (2초 후 자동 취소)
  // 두 번째 클릭: 실제 삭제. onDelete는 삭제 성공 후 호출 (실패 시 replyTo가 풀리지 않음)
  const handleDeleteClick = () => {
    if (!confirmDelete) {
      // 이전 타이머가 남아있으면 먼저 정리
      if (confirmTimerRef.current) clearTimeout(confirmTimerRef.current);
      setConfirmDelete(true);
      confirmTimerRef.current = setTimeout(() => setConfirmDelete(false), 2000);
      return;
    }
    remove.mutate(comment.id, { onSuccess: onDelete });
  };

  return (
    <div className="flex gap-3">
      <Link href={routes.user(comment.author.username)}>
        <Avatar src={comment.author.profileImageUrl} name={comment.author.username} size={small ? 28 : 32} />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between">
          <div className="text-[13px]">
            <span className="font-bold">{comment.author.username}</span>
            <span className="ml-1.5 text-[11px] text-ink-faint">{formatRelative(comment.createdAt)}</span>
          </div>
          {/* 본인 댓글에만 수정/삭제 표시 (삭제된 댓글은 버튼 없음) */}
          {isMine && !editing && !comment.deleted && (
            <div className="flex items-center gap-2 text-[12px]">
              <button type="button" onClick={() => { setEditText(comment.content ?? ""); setEditing(true); }} className="text-ink-faint">
                수정
              </button>
              {confirmDelete ? (
                <>
                  <span className="text-ink-sub">삭제할까요?</span>
                  <button type="button" onClick={handleDeleteClick} disabled={remove.isPending} className="font-bold text-danger">
                    확인
                  </button>
                  <button type="button" onClick={() => setConfirmDelete(false)} className="text-ink-faint">
                    취소
                  </button>
                </>
              ) : (
                <button type="button" onClick={handleDeleteClick} className="text-danger">
                  삭제
                </button>
              )}
            </div>
          )}
        </div>

        {comment.deleted ? (
          // 소프트 삭제된 댓글 — 대댓글이 남아있어 목록에 보이는 상태
          <p className="mt-0.5 text-[14px] text-ink-faint italic">삭제된 댓글입니다</p>
        ) : editing ? (
          <div className="mt-1 space-y-1">
            <textarea
              ref={editRef}
              value={editText}
              onChange={(e) => setEditText(e.target.value)}
              rows={2}
              maxLength={300}
              className="w-full resize-none rounded-lg bg-soft px-3 py-2 text-[14px] outline-none focus:ring-2 focus:ring-primary-deep/30"
            />
            <div className="flex gap-2 justify-end text-[12px]">
              <button type="button" onClick={() => setEditing(false)} className="text-ink-sub">취소</button>
              <button
                type="button"
                onClick={handleEditSave}
                disabled={update.isPending}
                className="font-bold text-primary-deep disabled:text-ink-faint"
              >
                저장
              </button>
            </div>
            {update.isError && <p className="text-[12px] text-danger">{errorMessage(update.error)}</p>}
          </div>
        ) : (
          <p className="mt-0.5 text-[14px] leading-relaxed whitespace-pre-line">{renderContent(comment.content ?? "")}</p>
        )}

        {!editing && !comment.deleted && (
          <button type="button" onClick={onReply} className="mt-1 text-[12px] font-medium text-ink-sub">
            답글 달기
          </button>
        )}
      </div>
    </div>
  );
}
