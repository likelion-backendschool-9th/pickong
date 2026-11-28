import { Users } from "lucide-react";
import { Avatar } from "@/shared/ui/display";
import type { Conversation } from "../model";

/**
 * 대화방 사진: 1:1은 상대 사진, 그룹은 나를 뺀 참여자 두 명을 겹쳐 보여 줍니다(없으면 사람 아이콘).
 */
export function ConversationAvatar({ conversation: c, size = 48 }: { conversation: Conversation; size?: number }) {
  if (c.type === "DIRECT") {
    return <Avatar src={c.partner?.profileImageUrl ?? null} name={c.partner?.username ?? ""} size={size} />;
  }
  const faces = c.members.filter((m) => !m.isMe).slice(0, 2);
  const face = Math.round(size * 0.66);
  if (faces.length === 0) {
    return (
      <span className="inline-flex shrink-0 items-center justify-center rounded-full bg-fill text-ink-sub" style={{ width: size, height: size }} aria-hidden>
        <Users size={Math.round(size * 0.45)} />
      </span>
    );
  }
  return (
    <span className="relative inline-block shrink-0" style={{ width: size, height: size }} aria-hidden>
      <span className="absolute top-0 left-0">
        <Avatar src={faces[0].profileImageUrl} name={faces[0].username} size={face} />
      </span>
      {faces[1] && (
        <span className="absolute right-0 bottom-0 rounded-full ring-2 ring-bg">
          <Avatar src={faces[1].profileImageUrl} name={faces[1].username} size={face} />
        </span>
      )}
    </span>
  );
}
