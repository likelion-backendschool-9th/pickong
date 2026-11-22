# 찜콩 (Pickong) — Common Blueprint

> 당신의 마침표. 누군가의 "따옴표"가 됩니다
> 취향과 경험이 쌓이는 소셜 지도

프론트엔드와 백엔드가 **함께 따르는** 설계 문서입니다.
각 영역의 폴더 구조와 계층 규칙은 영역별 문서에 있습니다.

| 문서 | 내용 |
| --- | --- |
| **common_blueprint.md** (이 문서) | 설계 기준, 저장소 구조, 기술 스택, 핵심 사용자 흐름, API 계약, 용어, 보류 범위, 기능 정리 |
| [frontend_blueprint.md](frontend_blueprint.md) | Next.js 폴더 구조, 계층 의존 규칙, Server/Client 구분, 상태 관리, 라우트 맵, 프론트 도메인 모델 |
| [backend_blueprint.md](backend_blueprint.md) | Spring Boot 헥사고날 구조, 모듈 구성, 의존 규칙, 영속성·보안·비동기 작업 |
| [AGENT.md](AGENT.md) | 브랜치 · 커밋 · PR · 리뷰 규칙 |
| ERD · API 명세서 (팀 공유 문서) | 테이블과 엔드포인트의 기준 |

**문서 우선순위:** 기획서(`찜콩_기획서.pdf`) → ERD · API 명세서 → 이 blueprint 문서들.
blueprint가 ERD·명세서와 다르면 임의로 맞추지 않고 팀에서 정한 뒤 양쪽을 같은 PR에서 고칩니다.

---

## 1. 설계 기준

**사람이 직접 개발하고 오래 유지보수하기 좋은 구조.** 초기 5명이 협업하고, 이후 기능이 계속 늘어나는 상황을 가정합니다.
패턴을 적용하는 것 자체는 목적이 아닙니다. 모든 규칙은 "이 코드를 바꿀 때 어디까지 영향이 가는가"를 줄이기 위해 존재합니다.

- 기능(도메인) 단위로 코드를 묶어서 담당자별로 병렬 개발합니다.
- 의존 방향은 한쪽으로만 흐르게 하고, 규칙은 리뷰가 아닌 도구(ESLint, ArchUnit)로 검사합니다.
- 외부 시스템(지도 SDK, 결제, LLM, 파일 저장소)은 경계 한 곳에 가두고, 바꿀 때 그 경계 안쪽만 수정합니다.
- 비어 있는 폴더나 쓰지 않는 추상화를 미리 만들지 않습니다.

---

## 2. 저장소 구조

```
pickong/
├── README.md                    저장소 안내 · 실행 방법
├── AGENT.md                     공통 개발 규칙 (브랜치 · 커밋 · PR)
├── common_blueprint.md          프론트·백엔드 공통 설계 (이 문서)
├── frontend_blueprint.md        프론트엔드 설계
├── backend_blueprint.md         백엔드 설계
├── .gitignore · .editorconfig   공통 설정
├── compose.yml                  로컬 실행용 컨테이너 (compose.override.yml · compose.prod.yml)
├── frontend/                    Next.js (App Router) · TypeScript · Tailwind
└── backend/                     Spring Boot 4.1 · Java 21 · Gradle
```

---

## 3. 기술 스택

| 영역 | 선택 | 자세히 |
| --- | --- | --- |
| 프론트엔드 | Next.js 16 (App Router) · React · TypeScript strict · Tailwind CSS · TanStack Query | [frontend_blueprint.md](frontend_blueprint.md) 0장 |
| 백엔드 | Spring Boot (버전 확정 필요) · Java 21 · Gradle 9.8 · 모듈러 모놀리스 + 헥사고날 | [backend_blueprint.md](backend_blueprint.md) 0장 · 8.7 |
| DB | PostgreSQL + PostGIS (`GEOGRAPHY(Point, 4326)`, GiST 인덱스) | |
| 파일 저장소 | Cloudflare R2 (S3 호환, Presigned URL 업로드) | |
| 지도 · 장소 | Kakao Maps JS SDK (프론트), Kakao 로컬 API (백엔드 프록시) | REST 키는 백엔드에만 둠 |
| 소셜 로그인 | Google OAuth2 | |
| 결제 | PG 연동 예정 (지금은 테스트 결제) | 제공자 확정 필요 |
| AI 링크 추출 | LLM API (제공자 미정, 실행 기록은 `import_extraction_runs`) | |
| API 문서 | springdoc OpenAPI (`/v3/api-docs`, `/swagger-ui.html`) | 프론트는 이 스펙으로 DTO 타입 생성 가능 |

---

## 4. 핵심 사용자 흐름

```
발견 → 저장 → 계획 → 방문 → 공유
```

| 단계 | 화면 |
| --- | --- |
| 발견 | 피드, 탐색, 공개 컬렉션, 외부 링크 가져오기 |
| 저장 | 게시물·장소·컬렉션의 "찜" 버튼, 링크 추출 결과 일괄 저장 |
| 계획 | 일정 편집 (순서 정하기 · 예약 있는 곳만 고정 시간) |
| 방문 | 방문 예정/완료 표시 |
| 공유 | 게시물 작성, 컬렉션·일정 공개 |

---

## 5. API 계약 (프론트 ↔ 백엔드)

엔드포인트 목록과 요청·응답 필드는 **API 명세서**가 기준입니다. 이 장은 모든 API에 공통으로 적용되는 형식을 정합니다.

### 5.1 경로

| 종류 | 경로 |
| --- | --- |
| REST API | `/api/v1/**` |
| 관리자 API | `/api/v1/admin/**` (회원과 별개인 관리자 계정으로 접근) |
| 소셜 로그인 시작 | `/oauth2/authorization/{provider}` (지금은 `google`) |
| WebSocket (STOMP) | 연결 `/ws`, 보내기 `/app/**`, 받기 `/user/queue/**` |
| 상태 확인 · 문서 | `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` |

- 브라우저는 같은 도메인의 `/api/*`로 요청하고, Next의 rewrites가 백엔드로 전달합니다. CORS 설정이 필요 없고 httpOnly 쿠키를 그대로 씁니다.
- **URL에는 DB 테이블 이름이 아니라 화면 용어를 씁니다.** 예: 게시물 테이블은 `posts`지만 경로는 `/feeds`입니다 (6장 용어표).

> **지금 프론트 mock과 명세서의 경로가 다릅니다.** 프론트 mock 핸들러(`frontend/mocks/handlers/`)는 이전 경로를 쓰고 있으므로, 백엔드 연결 전에 명세서 기준으로 맞춥니다.
>
> | 명세서 (기준) | 현재 mock |
> | --- | --- |
> | `/api/v1/**` | `/api/**` (버전 없음) |
> | `/api/v1/feeds`, `/feeds/{feedId}` | `/api/posts`, `/api/posts/:id` |
> | `POST·DELETE /feeds/{feedId}/likes`, `/bookmarks` | `/api/posts/:id/like`, `/bookmark` |
> | `/users/{username}/feeds` | `/api/users/:username/posts` |
> | `/place-imports` | `/api/imports` |
> | `PATCH /plans/{planId}/items/{itemId}/visit` | `POST /api/plans/:id/visits` |
> | `PATCH /notifications/{notificationId}/read` | `/api/notifications/read` |

### 5.2 오류 응답

```json
{ "status": 404, "code": "FEED_NOT_FOUND", "message": "사용자에게 보여 줄 문장" }
```

- HTTP 상태 코드와 `code`(대문자 스네이크)로 분기하고, 프론트는 `message`를 그대로 표시합니다. `status`는 HTTP 상태와 같은 값입니다(기존 응답 · 테스트 호환).
- 백엔드 오류 코드는 모듈마다 `ErrorCode`를 구현한 enum으로 둡니다 (`FeedErrorCode`, 공통은 `CommonErrorCode`).
- 입력값 검증 실패는 400 + `code: "INVALID_INPUT"`. 필드별 오류가 필요하면 `errors: [{ "field", "message" }]`를 추가합니다 (제안).

| 상태 | 언제 |
| --- | --- |
| 400 | 입력값 오류, 규칙 위반 (예: 가격이 100원 단위가 아님) |
| 401 | 로그인이 필요하거나 Access Token 만료 → 프론트는 재발급(`/auth/refresh`)을 한 번 시도하고 실패하면 로그인 화면으로 |
| 403 | 로그인했지만 권한 없음 (`FORBIDDEN`, 예: 다른 사람의 일정 수정). 상태를 바꾸는 요청의 CSRF 토큰이 없거나 틀리면 `INVALID_CSRF_TOKEN` → 프론트는 `XSRF-TOKEN` 쿠키를 다시 읽어 한 번 재시도 |
| 404 | 없거나, **존재 자체를 숨겨야 하는** 비공개 리소스 |
| 409 | 중복 (이미 팔로우함, 이미 구매함, 사용자명 중복) |

### 5.3 인증

| 항목 | 규칙 |
| --- | --- |
| 방식 | Access Token(JWT, 짧은 만료) + Refresh Token(임의 문자열, DB에는 SHA-256 해시만 저장) |
| 전달 | 둘 다 **httpOnly · Secure · SameSite=Lax 쿠키**. 프론트 JS는 토큰을 읽지 않습니다 |
| 재발급 | Refresh Token은 쓸 때마다 새로 발급(로테이션). 이미 교체된 토큰이 다시 오면 같은 family 전체를 폐기 |
| WebSocket | STOMP `CONNECT` 때 같은 쿠키로 인증 |
| 관리자 | `admins` 테이블의 별도 계정. 회원 토큰으로는 `/api/v1/admin/**`에 접근할 수 없습니다 |

쿠키 이름, 만료 시간, CSRF 대책(SameSite + Origin 검사 등)은 백엔드 구현 시 확정해서 이 표에 추가합니다.

### 5.4 식별자

| 리소스 | 경로·응답에 쓰는 값 |
| --- | --- |
| 게시물 · 장소 · 컬렉션 · 일정 | `public_id` (UUID). `{feedId}`, `{placeId}`, `{collectionId}`, `{planId}`는 모두 이 값 |
| 사용자 | `username` (`/users/{username}`) |
| 그 외 (댓글, 저장 장소, 일정 장소, 구매, 알림, 대화방, 추출 작업, 미디어, 정산, 신고, 제재, 약관) | DB `id`. 백엔드가 소유자·권한을 항상 확인 |

- JSON에서 모든 ID는 **문자열**로 보냅니다 (프론트 `Id = string`, 큰 숫자 정밀도 문제 방지).
- 공유 링크에 들어가는 리소스만 `public_id`를 씁니다. 순번 `id`가 밖에 노출되면 전체 개수나 다른 리소스 주소를 추측할 수 있기 때문입니다.

### 5.5 데이터 형식

| 종류 | 형식 | 예 |
| --- | --- | --- |
| 필드 이름 | camelCase | `likeCount`, `visitStatus` |
| 시각 | ISO-8601, 시간대 포함 (DB는 `TIMESTAMPTZ`) | `2026-11-20T14:30:00+09:00` |
| 날짜 / 시각만 | `YYYY-MM-DD` / `HH:mm` | `plan_date`, `fixed_time` |
| 금액 | 원 단위 정수 (KRW) | `price: 3000` |
| enum | DB 값 그대로 대문자 스네이크 문자열 | `PUBLIC`, `WANT_TO_GO` |
| 좌표 | `{ "lat": 37.54, "lng": 127.05 }` (WGS84) | |
| 지도 범위 | `bbox=서,남,동,북` (경도,위도,경도,위도) | `bbox=127.0,37.5,127.1,37.6` |

### 5.6 목록 · 페이지네이션 (제안)

- 무한 스크롤 목록(피드, 댓글, 메시지, 알림, 저장 장소)은 **커서 방식**: `?cursor=&size=` → `{ "items": [...], "nextCursor": "..." | null }`
- 관리자 화면처럼 페이지 번호가 필요한 목록만 `?page=&size=` → `{ "items", "page", "size", "totalCount" }`

### 5.7 공개 범위와 권한

- 공개 범위는 `PUBLIC`(전체) · `FOLLOWERS`(팔로워) · `PRIVATE`(나만) 세 가지이며, 게시물·컬렉션·장소·사용자 설정이 같은 값을 씁니다.
- **볼 수 있는지 판단은 백엔드가 합니다.** 프론트는 결과(데이터, 403, 404)를 표시만 합니다.
- 차단 관계, 유료 컬렉션 잠금(미리보기 2곳)도 백엔드가 응답에 반영해서 보냅니다 (`isLocked`, `lockedPlaceCount` 등).

---

## 6. 용어 대응표

같은 개념이 화면·URL·DB·코드에서 다른 이름을 쓰는 경우가 있어 한곳에 정리합니다.

| 개념 | API 경로 | DB 테이블 | 백엔드 모듈 | 프론트 |
| --- | --- | --- | --- | --- |
| 게시물 | `/feeds` | `feeds`, `feed_photos`, `feed_places` | `feed` | `entities/feed` |
| 댓글 · 좋아요 · 북마크 | `/feeds/{feedId}/comments`, `/likes`, `/bookmarks` | `comments`, `feed_likes`, `comment_likes`, `feed_bookmarks` | `feed` | `features/like-feed` 등 |
| 장소 | `/places` | `places` (+ 영업시간·외부 참조) | `place` | `entities/place` |
| 저장 장소 (찜) | `/saved-places` | `saved_places` | `savedplace` | `entities/place` (`SavedPlace`) |
| 컬렉션 · 유료 컬렉션 구매 | `/collections`, `/me/purchases` | `collections`, `collection_*` | `collection` | `entities/collection` |
| 일정 | `/plans` | `plans`, `plan_items`, `plan_item_reservations` | `plan` | `entities/plan` |
| AI 링크 가져오기 | `/place-imports` | `import_jobs`, `import_extraction_runs` | `placeimport` | `app/(main)/import/_api` |
| DM | `/conversations`, `/ws` | `conversations`, `conversation_members`, `messages` | `conversation` | `/messages` 라우트 |
| 크리에이터 · 정산 | `/creators`, `/me/creator/**` | `creators`, `creator_*` | `creator`, `settlement` | |
| 결제 · 환불 | `/payments`, `/purchases/{id}/refunds` | `payments`, `refunds` | `payment` | |

---

## 7. 보류 범위

API 명세서에서 **'보류'**로 표시한 기능입니다. 프론트·백엔드 모두 지금 만들지 않습니다.

| 기능 | API | 관련 테이블 |
| --- | --- | --- |
| 태그 (검색·인기·태그별 게시물/장소·내 태그 통계) | `/api/v1/tags/**`, `GET /api/v1/me/tag-stats` | `tags`, `feed_tags`, `place_tags`, `user_tag_stats` |
| 디바이스 등록·삭제 (푸시 토큰) | `POST /api/v1/me/devices`, `DELETE /api/v1/me/devices/{deviceId}` | `user_devices` |
| 지도 공개 범위 | `GET·PATCH /api/v1/me/map-settings` | (전용 컬럼 없음) |
| 알림 전체 읽음 | `POST /api/v1/notifications/read-all` | `notifications` |
| AI 일정 추천 | `POST /api/v1/plans/recommendations` | — |
| 일정 자동 점검 | — | 부록 A.7 |

보류를 풀 때는 명세서의 상태를 먼저 바꾸고, 백엔드 모듈 위치는 [backend_blueprint.md](backend_blueprint.md) 부록 A를 따릅니다.

> **예외:** 개인 태그(`/tags`, 장소별 태그 편집)와 디바이스 관리(`/settings/devices`)는 프론트 화면과 mock만 먼저 만들어 두었습니다. 명세서 상태는 바뀌지 않았으므로 백엔드는 계속 보류이고, API 경로도 mock 기준(`/api/me/tags`, `/api/me/devices`)입니다.

---

## 부록 A. 기능 정리

> 아래 API 경로는 프론트 mock 기준으로 적은 것입니다. 실제 계약은 5장과 API 명세서를 따릅니다.

기획서 7장의 출시 단계 기준입니다. **[껍데기]** 표시는 지금 프론트 기본 틀 단계에서 화면과 mock 데이터로 먼저 만들 대상입니다.

### A.1 인증·프로필 (1차 MVP)
- [껍데기] 회원가입 / 로그인 / 로그아웃
- [껍데기] 프로필 페이지: 게시물 · 컬렉션 · 방문 탭 (본인 프로필에는 일정 탭 추가)
  - 방문: 그 사용자가 게시물에 연결한 장소를 기록(목록) ↔ 지도로 전환해서 봄 (`GET /api/users/:username/posts`, `GET /api/users/:username/places`, 내가 볼 수 있는 게시물만). 저장한 장소 전체를 다루는 ‘내 지도’와 별개
  - 일정: 곧 떠나는 하루 + 지난 일정(방문 기록). 본인만 조회. `/plans`는 내 프로필 일정 탭으로 이동
- [껍데기] DM (1:1 메시지): 대화 목록(`/messages`, 대화 · 사람 검색) · 대화방(`/messages/[conversationId]`), 게시물 · 장소 공유 카드, 입력창 + 버튼으로 사진 · 장소 첨부, 프로필의 ‘메시지’ 버튼이나 검색으로 시작
  - API: `GET/POST /api/conversations`, `GET /api/conversations/unread-count`(하단바 배지), `GET /api/conversations/:id`, `GET/POST /api/conversations/:id/messages` (목록 조회 시 읽음 처리, 보낼 때 `attachment: { type: "IMAGE", url } | { type: "PLACE", placeId }`). 사진은 업로드 API 연결 전까지 브라우저 임시 URL
  - 실시간 연결 전까지 열린 대화방은 5초마다 새 메시지를 확인
- [껍데기] 팔로우 / 언팔로우, 팔로워·팔로잉 목록
- 프로필 편집 (사진, 소개)

### A.2 위치 기반 소셜 피드 (1차 MVP)
- [껍데기] 팔로잉 피드 (무한 스크롤) · 위에서 당겨서 새로고침 · 피드에서 하단바 홈을 다시 누르면 맨 위로 + 새로고침. 목록 · 지도 전환은 `?view=map`에 남겨 홈 버튼은 언제나 피드 목록으로
- [껍데기] 피드 지도: 지금 탭(팔로잉 · 추천)과 같은 기준의 게시물 장소를 지도에 표시하고, 지도 화면에 보이는 지역의 게시물을 아래 목록에 모두 표시 (`GET /api/feed/places?tab=`)
- [껍데기] 게시물 작성: 사진 업로드 + 후기 + **장소 태그(필수 요소)**
- [껍데기] 게시물 공개 범위: 공개 / 팔로워 공개 / 비공개
- [껍데기] 좋아요 · 댓글
- [껍데기] 게시물에서 장소 상세 보기
- [껍데기] **게시물의 장소를 내 지도에 바로 저장** (찜 버튼 → 컬렉션 선택 바텀시트)
- 광고·협찬 콘텐츠 표시 배지, 신고

### A.3 나만의 지도와 장소 컬렉션
- [껍데기] 장소 검색 및 저장 (1차)
- [껍데기] 저장한 장소를 개인 지도에 핀으로 표시 (1차)
- [껍데기] 내 지도는 일반 지도처럼 주변 장소를 함께 보여 주고, 저장한 곳만 초록 핀으로 구분 (`GET /api/places?bbox=서,남,동,북`). 저장하지 않은 장소를 누르면 바로 찜콩
- [껍데기] 내 지도 검색: 기본은 일반 장소 검색(결과를 누르면 지도에서 보여 줌), ‘저장한 곳만’ 체크 시 저장 장소 안에서만 검색
  - 카카오 지도 연결 후에는 지도 자체의 장소 표시 · 키워드 검색을 쓰고, 저장 핀만 올립니다
- [껍데기] 지역별 · 주제별 컬렉션 생성 (예: 성수동 숨겨진 카페, 제주도 노을 명소) (1차)
- [껍데기] 지도 · 컬렉션 공개 범위 설정 (1차)
- [껍데기] 카테고리 칩 편집: 보이기/숨기기 · 순서 바꾸기, 계정에 저장 (`GET/PUT /api/me/map-categories`). 카테고리는 장소의 종류라 이름 변경 · 새 카테고리 추가는 하지 않고, 나만의 분류는 컬렉션으로 거름
- 카테고리별 핀 색상 · 아이콘 구분
- 장소별 개인 메모 · 사진
- 지도 화면 범위 기반 조회, 핀 클러스터링
- 컬렉션 링크 공유
- 다른 사람의 공개 컬렉션에서 장소를 내 지도로 개별 저장
- 방문 예정 / 방문 완료 상태 표시 (2차)
- 커스텀 장소 핀: 정식 업체가 아닌 거리·풍경 명소를 좌표로 직접 등록 (2차)

### A.4 AI 외부 링크 장소 가져오기 (1차 MVP)
- [껍데기] 외부 URL 입력 (블로그, 일반 웹페이지 우선)
- [껍데기] 접근 불가 시 텍스트 직접 붙여넣기 대체 입력
- [껍데기] 추출 진행 상태 표시
- [껍데기] 추출 결과 미리보기: 장소명 · 주소 · 지도 위치
- [껍데기] 장소별 일치도 표시, 불분명한 장소는 **"확인 필요"** 표시 (자동 저장하지 않음)
- [껍데기] 사용자가 선택 · 수정 후 컬렉션에 **일괄 저장**
- 동일 장소 중복 표시
- 장소별 원본 출처 링크 기록
- 추출 정확도 · 중복 처리 개선 (2차)
- Instagram 등 추가 플랫폼은 공식 허용 범위 확인 후 확대

> 실제 추출(크롤링 · LLM · 지도 매칭)은 백엔드 담당. 프론트는 "입력 → 진행 → 후보 검토/수정 → 일괄 저장" 화면 흐름에 집중합니다.

### A.5 사람 중심의 장소 추천
- [껍데기] 탐색 화면: 인기 컬렉션, 크리에이터 목록
- 지인이 저장 · 추천한 공개 장소 탐색
- 장소 상세에서 **지인의 후기** 우선 노출
- 크리에이터 컬렉션 구독
- 저장한 장소의 추천 출처 기록 ("○○님 게시물에서 찜함")

### A.6 방문 일정 및 동선 관리
- [껍데기] 날짜별 일정 생성 (1차, 기본 수동 생성)
- [껍데기] 내 지도에서 방문 희망 장소 선택
- [껍데기] 방문 순서 지정 (드래그로 순서 변경) · 시간이 정해진 곳만 고정 시간 (체류 시간 · 시간표는 두지 않음)
- [껍데기] 계획 완료 · 다시 수정, 일정 공유(문구) · 삭제
- [껍데기] 방문 기록: 전체 선택, 일정에 없었지만 들른 곳 추가
- 장소별 영업시간 · 휴무일 확인 (2차)
- 장소 간 이동 시간 · 거리 표시 (2차)
- 기본 동선 추천 및 최적화 (2차)
- 일정 공유 (2차)
- 방문 완료 기록, 종료된 일정 보관 · 재사용

### A.7 일정 자동 점검 (보류)
> 일정 편집을 단순하게 바꾸면서 보류했습니다. 다시 넣는다면 막지 않고 안내만 하는 방식으로 검토합니다.

- 선택 날짜의 정기 휴무일 확인
- 영업시간과 방문 예정 시간 충돌 감지
- 일정 간 이동 시간 부족 감지
- 영업시간 정보가 없거나 불확실하면 확정하지 않고 **"확인 필요"** 표시
- 일정 변경 시 경로 재계산
- 날씨 정보 표시 (3차)
- AI 일정 추천 + 추천 이유 설명 (3차)

### A.8 확장 기능 (3차~4차)
- 위치 기반 스토리 · 체크인 (사진 1장 + 짧은 글, 시간 제한 노출, 위치 표시 지연)
- 크리에이터 페이지
- [껍데기] 유료 컬렉션 (단건 소액 결제)
  - 크리에이터(`isCreator`)만 전체 공개 컬렉션에 가격을 정할 수 있음 (500~50,000원, 100원 단위). 프로필에 ‘크리에이터’ 배지
  - 사지 않은 사람에게는 앞의 2곳만 미리보기, 나머지는 개수만 보여 주고 ‘N원에 전체 보기’로 구매. 한 번 사면 계속 열람
  - 지금은 테스트 결제만 (`POST /api/collections/:id/purchase` `{ method: "TEST", amount }`, 금액이 서버 가격과 다르면 거절). PG 연결 시 결제창 승인 결과를 서버에서 확인한 뒤 기록
  - 컬렉션 응답에 `price` · `isPurchased` · `isLocked`, 상세에 `lockedPlaceCount`
- Plus 구독 화면
- 알림
- 모바일 네이티브 앱, 다국어, 해외 지도
