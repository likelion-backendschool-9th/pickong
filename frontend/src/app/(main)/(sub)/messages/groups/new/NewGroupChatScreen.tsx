"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Notice, SectionTitle } from "@/shared/ui/display";
import { Field, Input } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { MemberPicker } from "../_components/MemberPicker";
import { useCreateGroupChat } from "../_api/groupChatApi";
import { GROUP_MAX_MEMBERS, GROUP_MIN_INVITES, GROUP_NAME_MAX, groupNameError } from "../_lib/group";

export function NewGroupChatScreen() {
  const router = useRouter();
  const toast = useToast();
  const create = useCreateGroupChat();
  const [name, setName] = useState("");
  const [memberIds, setMemberIds] = useState<string[]>([]);
  const ready = !groupNameError(name) && memberIds.length >= GROUP_MIN_INVITES;

  return (
    <Screen>
      <TopBar title="그룹 채팅 만들기" backHref={routes.groupChats()} />
      <div className="px-5 pt-2 pb-6">
        <Field label="채팅방 이름" htmlFor="group-name" hint={`${name.trim().length}/${GROUP_NAME_MAX}`}>
          <Input id="group-name" value={name} maxLength={GROUP_NAME_MAX} onChange={(e) => setName(e.target.value)} placeholder="예) 주말 성수 투어" />
        </Field>
        <SectionTitle className="mt-6 mb-1" action={<span className="text-[12px] text-ink-faint">{memberIds.length}명 선택 · 2명 이상</span>}>
          초대할 사람
        </SectionTitle>
        <MemberPicker selected={memberIds} onChange={setMemberIds} max={GROUP_MAX_MEMBERS - 1} />
        {create.isError && <Notice tone="danger" className="mt-3">{errorMessage(create.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!ready}
          loading={create.isPending}
          onClick={() =>
            create.mutate(
              { name: name.trim(), memberIds },
              {
                onSuccess: (g) => {
                  toast({ message: `‘${g.name}’ 채팅방을 만들었어요` });
                  router.replace(routes.groupChat(g.id));
                },
              },
            )
          }
        >
          {memberIds.length ? `${memberIds.length}명과 채팅방 만들기` : "채팅방 만들기"}
        </Button>
      </BottomCta>
    </Screen>
  );
}
