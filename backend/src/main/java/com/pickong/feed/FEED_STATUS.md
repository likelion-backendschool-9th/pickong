# Feed 모듈 구현 현황

> **이 파일이 피드 작업의 단일 소스**입니다.  
> `_local/feed-backend-tasks.md`는 더 이상 사용하지 않습니다.  
> 기준: `backend_blueprint.md` 3장 feed API 목록  
> 마지막 갱신: 2026-11-27, 실제 코드 + PR 기준 (PR #159 · #176 머지, #168 · #185 리뷰 중)

---

## API 구현 현황

### 피드 (Feed)

| 완료 | 메서드 | 경로 | UseCase 메서드 | 비고 |
| :---: | --- | --- | --- | --- |
| ✅ | GET | `/feeds?tab=following\|recommended` | `FeedUseCase.feed` | PR #92. 로그인 필수. 두 탭 모두 PUBLIC만 (FOLLOWERS 목록 포함은 #91, 아래 2-1) |
| ✅ | GET | `/feeds/places?tab=` | `FeedUseCase.feedPlaces` | PR #135 |
| ✅ | POST | `/feeds` | `FeedUseCase.write` | |
| ✅ | GET | `/feeds/{feedId}` | `FeedUseCase.getDetail` | 공개 범위 검사 (PR #176) |
| ✅ | PUT | `/feeds/{feedId}` | `FeedUseCase.update` | |
| ✅ | DELETE | `/feeds/{feedId}` | `FeedUseCase.delete` | 소프트 삭제, 204 (PR #185 리뷰 중) |
| ✅ | GET | `/users/{username}/feeds` | `FeedUseCase.getUserFeeds` | `UserFeedController` |
| ☐ | GET | `/me/bookmarked-feeds` | `FeedQueryUseCase.myBookmarks` | 미구현 |
| ☐ | GET | `/users/{username}/visits` | `FeedQueryUseCase.visitedPlacesOf` | 기준 미정 (blueprint 8.3 #3) |

### 댓글 (Comment)

| 완료 | 메서드 | 경로 | UseCase 메서드 | 비고 |
| :---: | --- | --- | --- | --- |
| ✅ | GET | `/feeds/{feedId}/comments` | `CommentUseCase.list` | 댓글 + 대댓글 일부 |
| ✅ | GET | `/feeds/{feedId}/comments/{commentId}/replies` | `CommentUseCase.replies` | |
| ✅ | POST | `/feeds/{feedId}/comments` | `CommentUseCase.write` | |
| ✅ | PUT | `/feeds/{feedId}/comments/{commentId}` | `CommentUseCase.edit` | |
| ✅ | DELETE | `/feeds/{feedId}/comments/{commentId}` | `CommentUseCase.delete` | soft delete |

### 반응 (Reaction)

| 완료 | 메서드 | 경로 | UseCase 메서드 | PR |
| :---: | --- | --- | --- | --- |
| ✅ | POST | `/feeds/{feedId}/likes` | `FeedReactionUseCase.likeFeed` | #62 |
| ✅ | DELETE | `/feeds/{feedId}/likes` | `FeedReactionUseCase.unlikeFeed` | #62 |
| ✅ | POST | `/feeds/{feedId}/bookmarks` | `FeedReactionUseCase.bookmark` | #62 |
| ✅ | DELETE | `/feeds/{feedId}/bookmarks` | `FeedReactionUseCase.unbookmark` | #62 |
| ✅ | POST | `/feeds/{feedId}/comments/{commentId}/likes` | `CommentReactionUseCase.likeComment` | #63 |
| ✅ | DELETE | `/feeds/{feedId}/comments/{commentId}/likes` | `CommentReactionUseCase.unlikeComment` | #63 |

---

## 미완성 항목

### ☐ 1. likedByMe / bookmarkedByMe 응답 필드 (issue #91)

현재 `FeedResponse`에 없음. 로그인 사용자 기준 좋아요·북마크 여부 추가 필요.

- `feed_likes`, `feed_bookmarks` LEFT JOIN 후 EXISTS 계산
- `FeedResponse`, `FeedPageResponse` 둘 다 수정 필요
- JOIN SQL은 `adapter/out/persistence/query/` 하위 QueryAdapter에서 처리 (blueprint 6.1)
- 비로그인 시 `likedByMe = false`, `bookmarkedByMe = false`

### ◐ 2. 사진 업로드 — Cloudflare R2 연동

```
1. 클라이언트 → POST /media/presigned           : Presigned PUT URL 발급             ✅ PR #168
2. 클라이언트 → R2 직접 PUT                                                          ☐ #170 (프론트)
3. 클라이언트 → POST /media/{mediaId}/confirm   : R2 HEAD로 크기와 Content-Type 검증  ✅ PR #168
4. POST /feeds {mediaFileIds}                   : 본인 · UPLOADED · 1:1 참조 검증       ☐ #155
5. 피드 응답 photos[].url                                                            ☐ #155
```

- R2 설정은 `backend/.env`의 `R2_*` 5개 (`backend/.env.example` 참고)
- Presigned URL은 `If-None-Match: *`가 서명돼 같은 URL로 다시 올릴 수 없음 (PR #168 리뷰 반영, 실제 R2 확인)
- 후속: 서버 측 이미지 처리 #182, 정리 스케줄러 #171, `DELETE /media/{mediaId}` #172, 사진별 장소 태그 계약 #155, 운영 R2 설정 #174

### ◐ 2-1. 게시물 공개 범위 (FOLLOWERS, PRIVATE)

- ✅ 단건 접근(상세, 댓글, 반응, 수정 · 삭제) 검사 — PR #176 (머지). `follows` 테이블은 PR #159 (V20)
- ☐ #91 목록(팔로잉 탭, 프로필 피드, 피드 지도)에 FOLLOWERS 게시물 포함 + `FeedJpaRepository`의 social `FollowJpaEntity` 직접 참조 제거 (blueprint 6.1). PR #95 커밋 `66c670d` 기준
- ☐ #178 신고 조건을 열람 기준과 맞추기
- ☐ #183 볼 수 없게 된 게시물에 남긴 내 댓글 · 반응의 삭제 권한 정책 (논의)
- ☐ #179 차단 (`user_blocks` 테이블 없음), 탈퇴 · 정지 회원 게시물 숨김은 #155 · #103

### ☐ 2-2. 오늘 검토에서 찾은 문제 (2026-11-27)

- ◐ 게시물 삭제가 물리 삭제 → `deleted_at` 소프트 삭제: PR #185 (리뷰 중)
- ☐ 카운터 미동기화: 게시물 작성 · 삭제 시 `users.feed_count` #184, 댓글 작성 · 삭제 시 `feeds.comment_count` (이슈 없음)
- ☐ 프론트 `PostDto`와 백엔드 `FeedResponse` 응답 계약 불일치 (작성자, `isLiked` · `isBookmarked`, 사진 URL, 내부 id 노출) — #155에서 처리
- ☐ `CreateFeedRequest` 입력 검증 (`content` 길이, `placeIds` 존재 확인)
- ☐ 삭제된 댓글 수정 가능 (`Comment.update`에 상태 검사 없음)
- 💡 상태 코드 통일 (게시물 작성 200 → 201. 삭제 204는 PR #185), 컨트롤러의 `isAdmin` 중복

### ☐ 3. GET /me/bookmarked-feeds

로그인 사용자의 북마크한 피드 목록. `feed_bookmarks` JOIN 조회.

### ☐ 4. feedId UUID 전환

`FeedController`, `CommentController`, `CommentReactionController` 모두 `Long feedId` (내부 PK) 사용 중.  
blueprint 8.6: `{feedId}`는 `feeds.public_id` (UUID) 사용 예정.  
전환 시점은 팀 결정 대기.

### ☐ 5. GET /users/{username}/visits

기준 미정 (blueprint 8.3 #3). 팀 협의 후 구현.

---

## 완료 이력

| PR | 내용 |
| --- | --- |
| #62 | 피드 좋아요·북마크 API |
| #63 | 댓글 좋아요 API |
| #85 | JWT 머지 → `@CurrentUser` 사용 가능 |
| #92 | `GET /feeds?tab=following\|recommended` 커서 페이지네이션 |
| #95 | ⚠️ 공개 범위 필터. `feature/86-feed-tab-query`로 머지되어 dev에는 없음 |
| #107 | 댓글·반응 컨트롤러 `userId` 파라미터 → `@CurrentUser` 교체 |
| #109 | 피드 생성 `userId`, 수정·삭제 소유자 검증 JWT 연동 |
| #135 | `GET /feeds/places` 지도용 장소 목록 |
| `UserFeedController` | `GET /users/{username}/feeds` |
| #159 | `follows` 테이블(V20), 팔로우 API를 로그인 사용자 기준으로 변경 (팔로잉 탭 500 해결) |
| #168 | 사진 업로드 API: Presigned URL 발급, 업로드 확인 (`media_files` V21) |
| #176 | 게시물 단건 접근 공개 범위 검사 (이슈 #175) |
