import Link from "next/link";
import type { ReactNode } from "react";
import { routes } from "@/shared/config/routes";
import { Avatar } from "@/shared/ui/display";
import type { User } from "../model";

export function UserRow({ user, trailing, description }: { user: User; trailing?: ReactNode; description?: ReactNode }) {
  return (
    <div className="flex items-center gap-3 py-2.5">
      <Link href={routes.user(user.username)} className="flex min-w-0 flex-1 items-center gap-3">
        <Avatar src={user.profileImageUrl} name={user.username} size={44} />
        <div className="min-w-0">
          <div className="truncate text-[14px] font-bold">{user.username}</div>
          <div className="truncate text-[12px] text-ink-sub">{description ?? (user.bio.split("\n")[0] || user.region)}</div>
        </div>
      </Link>
      {trailing}
    </div>
  );
}
