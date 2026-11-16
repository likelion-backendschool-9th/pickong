import { http } from "@/shared/api/http";
import type { UserDto } from "./dto";
import { toUser } from "./mapper";

export const userApi = {
  me: async () => toUser(await http.get<UserDto>("/me")),
  updateMe: async (input: { nickname: string; bio: string; region: string }) =>
    toUser(await http.patch<UserDto>("/me", input)),
  get: async (username: string) => toUser(await http.get<UserDto>(`/users/${username}`)),
  followers: async (username: string) => (await http.get<UserDto[]>(`/users/${username}/followers`)).map(toUser),
  following: async (username: string) => (await http.get<UserDto[]>(`/users/${username}/following`)).map(toUser),
  follow: async (username: string) => toUser(await http.post<UserDto>(`/users/${username}/follow`)),
  unfollow: async (username: string) => toUser(await http.delete<UserDto>(`/users/${username}/follow`)),
  /** 사용자 검색 (닉네임 · 아이디 · 지역 · 소개) */
  search: async (q: string, signal?: AbortSignal) =>
    (await http.get<{ items: UserDto[] }>("/search", { query: { q, type: "user" }, signal })).items.map(toUser),
};
