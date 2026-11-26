import { removeComment } from "./commentUtils";
import type { PostComment } from "./model";

const BASE_AUTHOR = {
  id: "u1",
  username: "user1",
  displayName: "User1",
  profileImageUrl: null,
};

function makeComment(overrides: Partial<PostComment>): PostComment {
  return {
    id: "c1",
    postId: "p1",
    parentId: null,
    author: BASE_AUTHOR,
    content: "내용",
    deleted: false,
    createdAt: "2024-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("removeComment", () => {
  it("대댓글이 없으면 대상 댓글을 목록에서 제거함", () => {
    const comments = [makeComment({ id: "c1" }), makeComment({ id: "c2" })];
    const { next, removedCount } = removeComment(comments, "c1");

    expect(next.map((c) => c.id)).toEqual(["c2"]);
    expect(removedCount).toBe(1);
  });

  it("살아있는 대댓글이 있으면 deleted: true로 마킹하고 목록에 유지함", () => {
    const comments = [
      makeComment({ id: "c1" }),
      makeComment({ id: "c2", parentId: "c1" }),
    ];
    const { next, removedCount } = removeComment(comments, "c1");

    const deleted = next.find((c) => c.id === "c1");
    expect(deleted?.deleted).toBe(true);
    expect(deleted?.content).toBeNull();
    expect(next).toHaveLength(2);
    expect(removedCount).toBe(1);
  });

  it("이미 삭제된(deleted: true) 대댓글은 살아있는 대댓글로 보지 않음", () => {
    const comments = [
      makeComment({ id: "c1" }),
      makeComment({ id: "c2", parentId: "c1", deleted: true, content: null }),
    ];
    const { next } = removeComment(comments, "c1");

    // 살아있는 대댓글이 없으므로 목록에서 제거됨
    expect(next.find((c) => c.id === "c1")).toBeUndefined();
  });

  it("존재하지 않는 commentId면 목록이 그대로 유지됨", () => {
    const comments = [makeComment({ id: "c1" })];
    const { next, removedCount } = removeComment(comments, "없는id");

    expect(next).toHaveLength(1);
    expect(removedCount).toBe(1);
  });
});
