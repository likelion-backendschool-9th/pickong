# Feed 모듈 구현 현황

> 기준: `backend_blueprint.md` 3장 feed API 목록  
> 브랜치: `feature/feed-domain` 기반 (PR #62 feed 반응, PR #63 댓글 반응)

---

## API 구현 체크리스트

### 피드 (Feed)

| 완료 | 메서드 | 경로 | UseCase 메서드 | 비고 |
| :---: | --- | --- | --- | --- |
| ✅ | POST | `/feeds` | `FeedCommandUseCase.write` | |
| ✅ | GET | `/feeds/{feedId}` | `FeedQueryUseCase.getDetail` | |
| ✅ | PUT | `/feeds/{feedId}` | `FeedCommandUseCase.update` | |
| ✅ | DELETE | `/feeds/{feedId}` | `FeedCommandUseCase.delete` | |
| ☐ | GET | `/feeds?tab=following\|recommended` | `FeedQueryUseCase.feed` | 추천은 비로그인 허용. 알고리즘 미정 |
| ☐ | GET | `/feeds/places?tab=` | `FeedQueryUseCase.feedPlaces` | |

### 댓글 (Comment)

| 완료 | 메서드 | 경로 | UseCase 메서드 | 비고 |
| :---: | --- | --- | --- | --- |
| ✅ | GET | `/feeds/{feedId}/comments` | `CommentQueryUseCase.list` | 댓글 + 대댓글 일부 |
| ✅ | POST | `/feeds/{feedId}/comments` | `CommentCommandUseCase.write` | |
| ✅ | PUT | `/feeds/{feedId}/comments/{commentId}` | `CommentCommandUseCase.edit` | |
| ✅ | DELETE | `/feeds/{feedId}/comments/{commentId}` | `CommentCommandUseCase.delete` | soft delete |
| ☐ | GET | `/feeds/{feedId}/comments/{commentId}/replies` | `CommentQueryUseCase.replies` | |

### 반응 (Reaction)

| 완료 | 메서드 | 경로 | UseCase 메서드 | PR |
| :---: | --- | --- | --- | --- |
| ✅ | POST | `/feeds/{feedId}/likes` | `FeedReactionUseCase.likeFeed` | #62 |
| ✅ | DELETE | `/feeds/{feedId}/likes` | `FeedReactionUseCase.unlikeFeed` | #62 |
| ✅ | POST | `/feeds/{feedId}/bookmarks` | `FeedReactionUseCase.bookmark` | #62 |
| ✅ | DELETE | `/feeds/{feedId}/bookmarks` | `FeedReactionUseCase.unbookmark` | #62 |
| ✅ | POST | `/feeds/{feedId}/comments/{commentId}/likes` | `FeedReactionUseCase.likeComment` | #63 |
| ✅ | DELETE | `/feeds/{feedId}/comments/{commentId}/likes` | `FeedReactionUseCase.unlikeComment` | #63 |

### 사용자별 조회

| 완료 | 메서드 | 경로 | UseCase 메서드 | 비고 |
| :---: | --- | --- | --- | --- |
| ☐ | GET | `/me/bookmarked-feeds` | `FeedQueryUseCase.myBookmarks` | JWT 머지 후 구현 |
| ☐ | GET | `/users/{username}/feeds` | `FeedQueryUseCase.feedsOf` | JWT 머지 후 구현 |
| ☐ | GET | `/users/{username}/visits` | `FeedQueryUseCase.visitedPlacesOf` | 기준 미정 (blueprint 8.3 #3) |

---

## 미완성 필드 (JWT 머지 전까지 보류)

피드 카드 응답에 아래 필드가 빠져 있음.  
JWT가 머지되어 `@CurrentUser`로 로그인 사용자 id를 받을 수 있게 되면 추가.

- `likedByMe` — `feed_likes` JOIN
- `bookmarkedByMe` — `feed_bookmarks` JOIN
- 비공개(`FOLLOWERS` / `PRIVATE`) 게시물 필터링 — 공개 범위 컬럼 기준 미정

> **구현 방식:** 조회 어댑터(`JpaFeedAdapter`)의 SQL에서 로그인 사용자 id로 LEFT JOIN.  
> 모듈 경계상 다른 모듈 JPA 엔티티·Repository는 쓰지 않고 SQL만 씀 (blueprint 6.3).  
> JOIN SQL은 `adapter/out/persistence/query/` 아래에 모아둠.

---

## feedId 식별자 이슈

> **blueprint 8.6:** `{feedId}`는 `feeds.public_id` (UUID) 사용 예정.  
> **현재 코드:** `FeedController`, `CommentController`, `CommentReactionController` 모두 `Long feedId` (내부 PK) 사용 중.  
>
> UUID로 전환 시 컨트롤러 → 서비스 → 리포지토리 전체 수정 필요.  
> 전환 시점은 팀원과 협의 후 결정. 전환 전까지 Swagger에 "내부 id 임시 사용" 주석 필요.

---

## 알고리즘 관련

### 팔로잉 피드 (`tab=following`)

```
로그인 사용자가 팔로우하는 사람들의 게시물을 최신순으로 반환
```

- `social` 모듈의 `follows` 테이블에서 `follower_id = 나` 인 `following_id` 목록 조회
- `feeds` 테이블에서 `user_id IN (팔로잉 목록)` 조건으로 필터 + `created_at DESC` 정렬
- 커서 기반 페이지네이션 (`cursor = last feedId`, 내림차순)
- 차단 사용자(`user_blocks`) 게시물 제외
- 비공개 게시물 처리: `FOLLOWERS` 공개 범위 확정 후 추가

> **의존:** `feed → social` (팔로잉 목록 조회)  
> 쿼리 어댑터에서 `follows` 테이블 직접 JOIN (다른 모듈 테이블 읽기 전용 허용, blueprint 6.3)

---

### 추천 피드 (`tab=recommended`)

```
비로그인 포함 전체 공개 게시물 중 "인기 있는" 게시물을 반환
```

> **현재 상태:** 알고리즘 미정. MVP에서는 단순 최신순으로 시작하고 이후 점수 기반으로 전환 예정.

**MVP 단계 (단순 최신순):**
- `feeds WHERE visibility = 'PUBLIC'` + `created_at DESC`
- 커서 기반 페이지네이션

**이후 단계 (점수 기반 정렬 — 미확정):**

```
score = like_count * W1 + comment_count * W2 + bookmark_count * W3 - age_hours * W4
```

- 가중치(`W1~W4`)는 팀이 튜닝
- 너무 오래된 게시물이 상위에 고정되지 않도록 시간 감쇠 적용
- 실시간 계산 대신 배치로 `score` 컬럼을 주기적으로 갱신하는 방식 검토
- 로그인 사용자라면 차단 사용자 게시물 제외

> **구현 시점:** 팔로잉 피드 완성 후. `feeds` 테이블에 `score NUMERIC` 컬럼 추가 필요 (Flyway 마이그레이션).

---

### 커서 페이지네이션

피드 목록 전체에 커서 방식 사용 (오프셋은 대용량 테이블에서 느림).

```
// 첫 요청
GET /feeds?tab=following&size=20

// 다음 페이지
GET /feeds?tab=following&cursor=<마지막_feedId>&size=20
```

- `cursor`가 없으면 첫 페이지
- 응답에 `nextCursor` 포함 (다음 페이지 없으면 null)
- `common/web/CursorPage<T>` 공통 타입 사용 (blueprint 4.5)

---

## 후속 작업 순서

```
1. JWT 머지
   └─ @CurrentUser 리졸버 생성
   └─ likedByMe / bookmarkedByMe 필드 추가
   └─ /me/bookmarked-feeds, /users/{username}/feeds 구현

2. GET /feeds?tab=following|recommended 구현
   └─ social 모듈 팔로잉 포트 확인 (RelationQueryUseCase)
   └─ 커서 페이지네이션 적용

3. GET /feeds/{feedId}/comments/{commentId}/replies 구현

4. feedId UUID 전환 (팀 결정 후)
```
