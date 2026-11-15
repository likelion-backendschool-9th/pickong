import { useQuery } from "@tanstack/react-query";
import { collectionApi, type Collection } from "@/entities/collection";
import { toPlace, type Place, type PlaceDto } from "@/entities/place";
import { postApi, type Post } from "@/entities/post";
import { toUser, type User, type UserDto } from "@/entities/user";
import { http } from "@/shared/api/http";

export type SearchType = "place" | "user" | "post" | "collection";

type Result =
  | { type: "place"; items: Place[] }
  | { type: "user"; items: User[] }
  | { type: "post"; items: Post[] }
  | { type: "collection"; items: Collection[] };

// 이 화면에서만 쓰는 통합 검색 API. 응답 항목은 각 엔티티 DTO와 같은 형태입니다.
async function search(q: string, type: SearchType): Promise<Result> {
  const res = await http.get<{ type: SearchType; items: unknown[] }>("/search", { query: { q, type } });
  switch (type) {
    case "place":
      return { type, items: (res.items as PlaceDto[]).map(toPlace) };
    case "user":
      return { type, items: (res.items as UserDto[]).map(toUser) };
    case "post":
      // 게시물·컬렉션은 엔티티 API의 매퍼를 재사용하기 위해 상세 형태를 그대로 받습니다.
      return { type, items: await Promise.all((res.items as { id: string }[]).map((p) => postApi.get(p.id))) };
    case "collection":
      return { type, items: await Promise.all((res.items as { id: string }[]).map((c) => collectionApi.get(c.id))) };
  }
}

export const useSearch = (q: string, type: SearchType) =>
  useQuery({ queryKey: ["search", type, q], queryFn: () => search(q, type), enabled: q.trim().length > 0 });
