"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { GROUP_MAX_MEMBERS, GROUP_MIN_MEMBERS, GROUP_TITLE_MAX, groupTitleError, useCreateGroup } from "@/entities/conversation";
import { useMe } from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Notice, SectionTitle } from "@/shared/ui/display";
import { Field, Input } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { UsernamePicker } from "../_components/UsernamePicker";
import type { PickedUser } from "../_lib/group";

/** 그룹 채팅 만들기: 이름 1~20자 + 아이디로 2~9명 초대 (나 포함 3~10명). 만들면 바로 대화방으로 */
export function NewGroupChatScreen() {
  const router = useRouter();
  const toast = useToast();
  const create = useCreateGroup();
  const { data: me } = useMe();
  const [title, setTitle] = useState("");
  const [picked, setPicked] = useState<PickedUser[]>([]);
  const minInvites = GROUP_MIN_MEMBERS - 1;
  const ready = !groupTitleError(title) && picked.length >= minInvites;

  return (
    <Screen>
      <TopBar title="그룹 채팅 만들기" backHref={routes.messages()} />
      <div className="px-5 pt-2 pb-6">
        <Field label="채팅방 이름" htmlFor="group-name" hint={`${[...title.trim()].length}/${GROUP_TITLE_MAX}`}>
          <Input id="group-name" value={title} maxLength={GROUP_TITLE_MAX * 2} onChange={(e) => setTitle(e.target.value)} placeholder="예) 주말 성수 투어" />
        </Field>
        <SectionTitle
          className="mt-6 mb-2"
          action={<span className="text-[12px] text-ink-faint">{picked.length}명 · 나 포함 {GROUP_MIN_MEMBERS}~{GROUP_MAX_MEMBERS}명</span>}
        >
          초대할 사람
        </SectionTitle>
        <UsernamePicker picked={picked} onChange={setPicked} taken={me ? [me.username] : []} seats={GROUP_MAX_MEMBERS - 1} />
        {create.isError && <Notice tone="danger" className="mt-3">{errorMessage(create.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!ready}
          loading={create.isPending}
          onClick={() =>
            create.mutate(
              { title: title.trim(), usernames: picked.map((p) => p.username) },
              {
                onSuccess: (g) => {
                  toast({ message: `‘${g.title}’ 채팅방을 만들었어요` });
                  router.replace(routes.conversation(g.id));
                },
              },
            )
          }
        >
          {picked.length >= minInvites ? `${picked.length}명과 채팅방 만들기` : `${minInvites}명 이상 초대해 주세요`}
        </Button>
      </BottomCta>
    </Screen>
  );
}
