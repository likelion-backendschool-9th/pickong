import { http } from "@/shared/api/http";
import type { UserDto } from "./dto";
import { toUser } from "./mapper";

export const userApi = {
  me: async () => toUser(await http.get<UserDto>("/me")),
  /** 프로필 수정. username은 닉네임(= 사용자 ID), name은 이름(중복 가능, null이면 이름 없음)입니다. */
  updateMe: async (input: { username: string; name: string | null; bio: string; region: string }) =>
    toUser(await http.patch<UserDto>("/me", input)),
  /** 닉네임(= 사용자 ID) 중복 확인. 대소문자를 무시하고 비교합니다. 가입 전 온보딩에서도 부를 수 있습니다. */
  nicknameAvailability: (nickname: string, signal?: AbortSignal) =>
    http.get<{ available: boolean }>("/auth/username-availability", { query: { username: nickname }, signal }),
  get: async (username: string) => toUser(await http.get<UserDto>(`/users/${username}`)),
  followers: async (username: string) => (await http.get<UserDto[]>(`/users/${username}/followers`)).map(toUser),
  following: async (username: string) => (await http.get<UserDto[]>(`/users/${username}/following`)).map(toUser),
  follow: async (username: string) => toUser(await http.post<UserDto>(`/users/${username}/follow`)),
  unfollow: async (username: string) => toUser(await http.delete<UserDto>(`/users/${username}/follow`)),
  /** 사용자 검색 (닉네임 · 아이디 · 지역 · 소개) */
  search: async (q: string, signal?: AbortSignal) =>
    (await http.get<{ items: UserDto[] }>("/search", { query: { q, type: "user" }, signal })).items.map(toUser),
};
