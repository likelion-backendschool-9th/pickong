"use client";

import { useMe } from "@/entities/user";
import { routes } from "@/shared/config/routes";
import { TopBar } from "@/shared/ui/layout";
import { SettingsMenu } from "../../_components/SettingsMenu";

export default function SettingsPage() {
  const { data: me } = useMe();
  return (
    <div>
      <TopBar title="계정 설정" backHref={me ? routes.user(me.username) : routes.feed()} />
      <h2 className="px-5 pt-2 pb-3 text-[20px] font-bold">{me?.nickname ?? ""}님의 계정</h2>
      <SettingsMenu />
    </div>
  );
}
