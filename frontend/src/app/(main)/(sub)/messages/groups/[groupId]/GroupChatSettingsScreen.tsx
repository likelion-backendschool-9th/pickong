"use client";

import { Crown, LogOut, Pencil, UserPlus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Switch } from "@/shared/ui/controls";
import { Avatar, Badge, ErrorState, Notice, SectionTitle, Skeleton } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { MemberPicker } from "../_components/MemberPicker";
import {
  useGroupChat,
  useInviteMembers,
  useLeaveGroupChat,
  useRemoveMember,
  useUpdateGroupChat,
  type GroupChat,
  type GroupMember,
} from "../_api/groupChatApi";
import { GROUP_NAME_MAX, groupNameError, remainingSeats } from "../_lib/group";

type Sheet = { type: "rename" } | { type: "invite" } | { type: "remove"; member: GroupMember } | { type: "leave" } | null;

/** 그룹 채팅방 관리: 이름 변경(방장) · 멤버 초대 · 내보내기(방장) · 알림 끄기 · 나가기 */
export function GroupChatSettingsScreen({ id }: { id: string }) {
  const { data, isPending, isError, error, refetch } = useGroupChat(id);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;
  return <Settings g={data} />;
}

function Settings({ g }: { g: GroupChat }) {
  const router = useRouter();
  const toast = useToast();
  const [sheet, setSheet] = useState<Sheet>(null);
  const update = useUpdateGroupChat(g.id);
  const remove = useRemoveMember(g.id);
  const leave = useLeaveGroupChat(g.id);
  const seats = remainingSeats(g.members.length);

  return (
    <div className="pb-10">
      <TopBar title="채팅방 관리" backHref={routes.groupChats()} />

      <section className="flex flex-col items-center px-5 pt-4 pb-6 text-center">
        <h2 className="flex items-center gap-1.5 text-[20px] font-bold">
          {g.name}
          {g.isOwner && (
            <button type="button" onClick={() => setSheet({ type: "rename" })} aria-label="채팅방 이름 바꾸기" className="inline-flex size-8 items-center justify-center rounded-full text-ink-sub active:bg-fill">
              <Pencil size={16} />
            </button>
          )}
        </h2>
        <p className="mt-1 text-[12px] text-ink-sub">
          {g.members.length}명 · {formatDotDate(g.createdAt)} 개설 · 방장 {g.owner.username}
        </p>
      </section>

      <div className="mx-5 flex items-center gap-3 rounded-card bg-soft px-4 py-3.5">
        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-bold">채팅방 알림</p>
          <p className="text-[12px] text-ink-sub">{g.muted ? "이 채팅방의 새 메시지 알림을 받지 않아요" : "새 메시지가 오면 푸시로 알려 드려요"}</p>
        </div>
        <Switch label="채팅방 알림" hideLabel checked={!g.muted} onChange={(on) => update.mutate({ muted: !on })} />
      </div>

      <section className="mt-6 px-5">
        <SectionTitle className="mb-1" action={<span className="text-[12px] text-ink-faint">최대 30명</span>}>
          참여자 {g.members.length}
        </SectionTitle>
        <button
          type="button"
          onClick={() => setSheet({ type: "invite" })}
          disabled={seats === 0}
          className="flex w-full items-center gap-3 py-2.5 text-left text-[14px] font-bold text-primary-deep disabled:text-ink-faint"
        >
          <span className="flex size-10 items-center justify-center rounded-full bg-selected">
            <UserPlus size={18} />
          </span>
          {seats === 0 ? "인원이 가득 찼어요" : "친구 초대하기"}
        </button>
        <ul className="divide-y divide-line">
          {g.members.map((m) => (
            <li key={m.id} className="flex items-center gap-3 py-2.5">
              <Link href={routes.user(m.username)} className="flex min-w-0 flex-1 items-center gap-3">
                <Avatar src={m.profileImageUrl} name={m.username} size={40} />
                <span className="min-w-0">
                  <span className="flex items-center gap-1 text-[14px] font-bold">
                    <span className="truncate">{m.username}</span>
                    {m.isMe && <Badge tone="neutral">나</Badge>}
                    {m.isOwner && <Crown size={13} className="shrink-0 text-primary-deep" aria-label="방장" />}
                  </span>
                </span>
              </Link>
              {g.isOwner && !m.isMe && (
                <button type="button" onClick={() => setSheet({ type: "remove", member: m })} className="h-8 rounded-control border border-line px-2.5 text-[12px] text-ink-sub">
                  내보내기
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>

      {update.isError && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(update.error)}</Notice>}

      <button type="button" onClick={() => setSheet({ type: "leave" })} className="mx-5 mt-8 inline-flex items-center gap-1.5 text-[14px] font-medium text-danger">
        <LogOut size={16} /> 채팅방 나가기
      </button>

      <RenameSheet open={sheet?.type === "rename"} g={g} onClose={() => setSheet(null)} />
      <InviteSheet open={sheet?.type === "invite"} g={g} seats={seats} onClose={() => setSheet(null)} />

      <BottomSheet open={sheet?.type === "remove"} onClose={() => setSheet(null)} title="내보낼까요?">
        {sheet?.type === "remove" && (
          <ConfirmBody
            message={`${sheet.member.username}님을 채팅방에서 내보내요. 다시 초대하기 전까지 대화를 볼 수 없어요.`}
            confirmLabel="내보내기"
            loading={remove.isPending}
            error={remove.isError ? remove.error : null}
            onCancel={() => setSheet(null)}
            onConfirm={() =>
              remove.mutate(sheet.member.id, {
                onSuccess: () => {
                  toast({ message: `${sheet.member.username}님을 내보냈어요` });
                  setSheet(null);
                },
              })
            }
          />
        )}
      </BottomSheet>

      <BottomSheet open={sheet?.type === "leave"} onClose={() => setSheet(null)} title="채팅방을 나갈까요?">
        <ConfirmBody
          message={
            g.isOwner && g.members.length > 1
              ? "방장이 나가면 가장 먼저 들어온 참여자가 방장이 돼요. 나가면 대화 내용이 내 목록에서 사라져요."
              : "나가면 대화 내용이 내 목록에서 사라져요."
          }
          confirmLabel="나가기"
          loading={leave.isPending}
          error={leave.isError ? leave.error : null}
          onCancel={() => setSheet(null)}
          onConfirm={() =>
            leave.mutate(undefined, {
              onSuccess: () => {
                toast({ message: `‘${g.name}’에서 나왔어요` });
                router.replace(routes.groupChats());
              },
            })
          }
        />
      </BottomSheet>
    </div>
  );
}

function ConfirmBody({
  message,
  confirmLabel,
  loading,
  error,
  onCancel,
  onConfirm,
}: {
  message: string;
  confirmLabel: string;
  loading: boolean;
  error: unknown;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <p className="text-[14px] leading-relaxed text-ink-sub">{message}</p>
      {!!error && <Notice tone="danger" className="mt-3">{errorMessage(error)}</Notice>}
      <div className="mt-5 grid grid-cols-2 gap-2">
        <Button variant="outline" block onClick={onCancel}>
          취소
        </Button>
        <Button variant="danger" block loading={loading} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  );
}

function RenameSheet({ open, g, onClose }: { open: boolean; g: GroupChat; onClose: () => void }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="채팅방 이름">
      {open && <RenameForm g={g} onClose={onClose} />}
    </BottomSheet>
  );
}

function RenameForm({ g, onClose }: { g: GroupChat; onClose: () => void }) {
  const [name, setName] = useState(g.name);
  const update = useUpdateGroupChat(g.id);
  const error = groupNameError(name);
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <Input aria-label="채팅방 이름" value={name} maxLength={GROUP_NAME_MAX} onChange={(e) => setName(e.target.value)} autoFocus />
      <p className="mt-1.5 text-right text-[12px] text-ink-faint">
        {name.trim().length}/{GROUP_NAME_MAX}
      </p>
      {update.isError && <Notice tone="danger" className="mt-2">{errorMessage(update.error)}</Notice>}
      <Button block className="mt-3" disabled={!!error || name.trim() === g.name} loading={update.isPending} onClick={() => update.mutate({ name: name.trim() }, { onSuccess: onClose })}>
        저장
      </Button>
    </div>
  );
}

function InviteSheet({ open, g, seats, onClose }: { open: boolean; g: GroupChat; seats: number; onClose: () => void }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="친구 초대">
      {open && <InviteForm g={g} seats={seats} onClose={onClose} />}
    </BottomSheet>
  );
}

function InviteForm({ g, seats, onClose }: { g: GroupChat; seats: number; onClose: () => void }) {
  const [ids, setIds] = useState<string[]>([]);
  const invite = useInviteMembers(g.id);
  const toast = useToast();
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <div className="max-h-[50dvh] overflow-y-auto">
        <MemberPicker selected={ids} onChange={setIds} excludeIds={g.members.map((m) => m.id)} max={seats} />
      </div>
      {invite.isError && <Notice tone="danger" className="mt-2">{errorMessage(invite.error)}</Notice>}
      <Button
        block
        className="mt-3"
        disabled={!ids.length}
        loading={invite.isPending}
        onClick={() =>
          invite.mutate(ids, {
            onSuccess: () => {
              toast({ message: `${ids.length}명을 초대했어요` });
              onClose();
            },
          })
        }
      >
        {ids.length ? `${ids.length}명 초대` : "초대할 친구를 골라 주세요"}
      </Button>
    </div>
  );
}
