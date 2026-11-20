import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UserSummary } from "@/entities/user";
import { http } from "@/shared/api/http";

/* 그룹 채팅방 관리 */

export interface GroupMember extends UserSummary {
  isOwner: boolean;
  isMe: boolean;
}

export interface GroupChat {
  id: string;
  name: string;
  owner: UserSummary;
  members: GroupMember[];
  isOwner: boolean;
  muted: boolean;
  createdAt: string;
  updatedAt: string;
}

const groupsKey = ["group-chats"] as const;
const groupKey = (id: string) => [...groupsKey, id] as const;

export const useGroupChats = () => useQuery({ queryKey: groupsKey, queryFn: () => http.get<GroupChat[]>("/group-chats") });
export const useGroupChat = (id: string) => useQuery({ queryKey: groupKey(id), queryFn: () => http.get<GroupChat>(`/group-chats/${id}`) });

export function useCreateGroupChat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; memberIds: string[] }) => http.post<GroupChat>("/group-chats", input),
    onSuccess: () => qc.invalidateQueries({ queryKey: groupsKey }),
  });
}

/** 방 정보를 바꾸는 요청들: 응답(최신 방 정보)으로 캐시를 바로 갱신합니다. */
function useGroupMutation<T>(id: string, fn: (input: T) => Promise<GroupChat>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (g) => {
      qc.setQueryData(groupKey(id), g);
      qc.invalidateQueries({ queryKey: groupsKey, exact: true });
    },
  });
}

export const useUpdateGroupChat = (id: string) =>
  useGroupMutation(id, (input: { name?: string; muted?: boolean }) => http.patch<GroupChat>(`/group-chats/${id}`, input));

export const useInviteMembers = (id: string) =>
  useGroupMutation(id, (userIds: string[]) => http.post<GroupChat>(`/group-chats/${id}/members`, { userIds }));

export const useRemoveMember = (id: string) =>
  useGroupMutation(id, (userId: string) => http.delete<GroupChat>(`/group-chats/${id}/members/${userId}`));

export function useLeaveGroupChat(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<void>(`/group-chats/${id}/leave`),
    onSuccess: () => {
      qc.removeQueries({ queryKey: groupKey(id) });
      qc.invalidateQueries({ queryKey: groupsKey });
    },
  });
}
