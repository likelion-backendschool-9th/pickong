"use client";

import { LogOut, Pencil, UserPlus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  GROUP_MAX_MEMBERS,
  GROUP_TITLE_MAX,
  groupTitleError,
  useConversation,
  useInviteMembers,
  useLeaveConversation,
  useUpdateConversation,
  type Conversation,
} from "@/entities/conversation";
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
import { UsernamePicker } from "../_components/UsernamePicker";
import { remainingSeats, type PickedUser } from "../_lib/group";

type Sheet = "rename" | "invite" | "leave" | null;

/**
 * 그룹 채팅방 설정: 이름 변경 · 초대(멤버 누구나, 아이디로) · 알림 끄기 · 나가기.
 * 방장이 없고 내보내기는 없습니다. 1:1 대화방이면 대화방으로 돌려보냅니다.
 */
export function GroupChatSettingsScreen({ id }: { id: string }) {
  const { data, isPending, isError, error, refetch } = useConversation(id);

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;
  if (data.type !== "GROUP") return <ErrorState message="그룹 채팅에서만 볼 수 있어요." />;
  return <Settings g={data} />;
}

function Settings({ g }: { g: Conversation }) {
  const router = useRouter();
  const toast = useToast();
  const [sheet, setSheet] = useState<Sheet>(null);
  const update = useUpdateConversation(g.id);
  const leave = useLeaveConversation(g.id);
  const seats = remainingSeats(g.memberCount);

  return (
    <div className="pb-10">
      <TopBar title="채팅방 설정" backHref={routes.conversation(g.id)} />

      <section className="flex flex-col items-center px-5 pt-4 pb-6 text-center">
        <h2 className="flex items-center gap-1.5 text-[20px] font-bold">
          {g.title}
          <button type="button" onClick={() => setSheet("rename")} aria-label="채팅방 이름 바꾸기" className="inline-flex size-8 items-center justify-center rounded-full text-ink-sub active:bg-fill">
            <Pencil size={16} />
          </button>
        </h2>
        <p className="mt-1 text-[12px] text-ink-sub">
          {g.memberCount}명 · {formatDotDate(g.createdAt)} 개설
        </p>
      </section>

      <div className="mx-5 flex items-center gap-3 rounded-card bg-soft px-4 py-3.5">
        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-bold">채팅방 알림</p>
          <p className="text-[12px] text-ink-sub">{g.muted ? "이 채팅방의 새 메시지 알림을 받지 않아요" : "새 메시지가 오면 알려 드려요"}</p>
        </div>
        <Switch label="채팅방 알림" hideLabel checked={!g.muted} onChange={(on) => update.mutate({ muted: !on })} />
      </div>

      <section className="mt-6 px-5">
        <SectionTitle className="mb-1" action={<span className="text-[12px] text-ink-faint">최대 {GROUP_MAX_MEMBERS}명</span>}>
          참여자 {g.memberCount}
        </SectionTitle>
        <button
          type="button"
          onClick={() => setSheet("invite")}
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
            <li key={m.id} className="py-2.5">
              <Link href={routes.user(m.username)} className="flex min-w-0 items-center gap-3">
                <Avatar src={m.profileImageUrl} name={m.username} size={40} />
                <span className="flex min-w-0 items-center gap-1 text-[14px] font-bold">
                  <span className="truncate">{m.username}</span>
                  {m.isMe && <Badge tone="neutral">나</Badge>}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      {update.isError && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(update.error)}</Notice>}

      <button type="button" onClick={() => setSheet("leave")} className="mx-5 mt-8 inline-flex items-center gap-1.5 text-[14px] font-medium text-danger">
        <LogOut size={16} /> 채팅방 나가기
      </button>

      <BottomSheet open={sheet === "rename"} onClose={() => setSheet(null)} title="채팅방 이름">
        {sheet === "rename" && <RenameForm g={g} onClose={() => setSheet(null)} />}
      </BottomSheet>

      <BottomSheet open={sheet === "invite"} onClose={() => setSheet(null)} title="친구 초대">
        {sheet === "invite" && <InviteForm g={g} seats={seats} onClose={() => setSheet(null)} />}
      </BottomSheet>

      <BottomSheet open={sheet === "leave"} onClose={() => setSheet(null)} title="채팅방을 나갈까요?">
        <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
          <p className="text-[14px] leading-relaxed text-ink-sub">나가면 이 채팅방이 내 목록에서 사라져요. 다시 초대받으면 이전 대화도 다시 볼 수 있어요.</p>
          {leave.isError && <Notice tone="danger" className="mt-3">{errorMessage(leave.error)}</Notice>}
          <div className="mt-5 grid grid-cols-2 gap-2">
            <Button variant="outline" block onClick={() => setSheet(null)}>
              취소
            </Button>
            <Button
              variant="danger"
              block
              loading={leave.isPending}
              onClick={() =>
                leave.mutate(undefined, {
                  onSuccess: () => {
                    toast({ message: `‘${g.title}’에서 나왔어요` });
                    router.replace(routes.messages());
                  },
                })
              }
            >
              나가기
            </Button>
          </div>
        </div>
      </BottomSheet>
    </div>
  );
}

function RenameForm({ g, onClose }: { g: Conversation; onClose: () => void }) {
  const [title, setTitle] = useState(g.title ?? "");
  const update = useUpdateConversation(g.id);
  const error = groupTitleError(title);
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <Input aria-label="채팅방 이름" value={title} maxLength={GROUP_TITLE_MAX * 2} onChange={(e) => setTitle(e.target.value)} autoFocus />
      <p className="mt-1.5 text-right text-[12px] text-ink-faint">
        {[...title.trim()].length}/{GROUP_TITLE_MAX}
      </p>
      {update.isError && <Notice tone="danger" className="mt-2">{errorMessage(update.error)}</Notice>}
      <Button
        block
        className="mt-3"
        disabled={!!error || title.trim() === g.title}
        loading={update.isPending}
        onClick={() => update.mutate({ title: title.trim() }, { onSuccess: onClose })}
      >
        저장
      </Button>
    </div>
  );
}

function InviteForm({ g, seats, onClose }: { g: Conversation; seats: number; onClose: () => void }) {
  const [picked, setPicked] = useState<PickedUser[]>([]);
  const invite = useInviteMembers(g.id);
  const toast = useToast();
  return (
    <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
      <UsernamePicker picked={picked} onChange={setPicked} taken={g.members.map((m) => m.username)} seats={seats} />
      {invite.isError && <Notice tone="danger" className="mt-2">{errorMessage(invite.error)}</Notice>}
      <Button
        block
        className="mt-4"
        disabled={!picked.length}
        loading={invite.isPending}
        onClick={() =>
          invite.mutate(
            picked.map((p) => p.username),
            {
              onSuccess: () => {
                toast({ message: `${picked.length}명을 초대했어요` });
                onClose();
              },
            },
          )
        }
      >
        {picked.length ? `${picked.length}명 초대` : "초대할 친구의 아이디를 추가해 주세요"}
      </Button>
    </div>
  );
}
