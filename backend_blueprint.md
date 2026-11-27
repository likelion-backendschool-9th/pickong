# 찜콩 (Pickong) — Backend Blueprint

ERD와 API 명세서('보류' 제외)를 기준으로 정리한 **백엔드 아키텍처 설계 문서**입니다.
하나의 Spring Boot 프로젝트 안에서 도메인별 패키지를 나누는 **모듈러 모놀리스**이고, 각 모듈 안은 **헥사고날(Ports & Adapters) + 클린 아키텍처(의존성 역전)** 구조입니다.

> 공통 내용(API 계약, 오류 형식, 인증 방식, 식별자 규칙, 용어, 보류 범위)은 [common_blueprint.md](common_blueprint.md)에 있습니다.
> 이 문서는 **설계 검토용**입니다. 패키지와 클래스는 아직 만들지 않았고, 설계를 확정한 뒤 모듈별로 필요한 것만 만듭니다.

**설계 기준:** 5명의 백엔드 개발자가 직접 개발하고 오래 유지보수하기 좋은 구조.
아키텍처 이름에 맞추려고 만드는 인터페이스 · DTO · Mapper는 두지 않습니다. 모든 계층과 파일은 "이것이 없으면 무엇을 바꿀 때 어디까지 영향이 가는가"로 존재 이유를 설명할 수 있어야 합니다.

| 장 | 내용 |
| --- | --- |
| 0 | 전제 (기술 스택 · 원칙) |
| 1 | 최상위 패키지 구조의 문제점과 개선 |
| 2 | 도메인 책임과 테이블 소유권 |
| 3 | API ↔ 도메인 매핑 |
| 4 | 전체 패키지 트리와 모듈 내부 규칙 |
| 5 | 핵심 영역 상세 설계 (Plan · Collection/Payment/Settlement · PlaceImport · Conversation) |
| 6 | 모듈 간 의존 · 이벤트 · 트랜잭션 |
| 7 | ArchUnit 검증 규칙 · 테스트 책임 |
| 8 | ERD / API 불일치와 미결정 사항 |
| 9 | 기능을 추가할 때의 패키지 배치 규칙 |
| 부록 A | 보류 기능을 다시 넣을 때의 위치 |

---

## 0. 전제

### 0.1 기술 스택

| 구분 | 선택 | 비고 |
| --- | --- | --- |
| 언어 | Java 21 | record, sealed interface, switch 패턴 매칭 사용 |
| 프레임워크 | Spring Boot | ⚠️ **버전 확정 필요**: 요청서는 3.4, 현재 `backend/build.gradle`은 4.1.2 (8.7 #1) |
| DB | PostgreSQL 16 + PostGIS | `GEOGRAPHY(Point, 4326)`, GiST 인덱스, 부분 UNIQUE 인덱스 |
| 영속성 | Spring Data JPA (+ Hibernate Spatial) | 화면 조회는 JdbcClient 네이티브 SQL 또는 QueryDSL (팀 결정) |
| 마이그레이션 | Flyway (`spring-boot-starter-flyway`) | `resources/db/migration`. V1은 인증 테이블만, 나머지는 각 모듈이 V2부터 추가. 스키마는 Flyway만 만들고 `ddl-auto: none` |
| 보안 | Spring Security 7, OAuth2 Client(카카오 · Google), JWT(Spring Security의 Nimbus 인코더 · 디코더) | 이메일 · 비밀번호 로그인 없음. 보안 설정 상세는 아래 '보안 결정' |
| 실시간 | Spring WebSocket + STOMP | DM |
| 테스트 | JUnit 5, ArchUnit, Testcontainers(PostGIS 16) | `TestcontainersConfiguration`. Docker가 없으면 DB 테스트는 실패(건너뛰지 않음) |

패키지 구조는 Spring Boot 3.4와 4.x 어느 쪽이어도 똑같이 적용됩니다.

### 0.2 원칙 요약

1. **모듈은 비즈니스 책임과 데이터 소유권으로 나눕니다.** URL 접두사(`/admin`, `/me`)로 나누지 않습니다.
2. **테이블마다 소유 모듈은 하나입니다.** 다른 모듈은 그 테이블에 쓰지 않습니다.
3. **domain은 순수 Java입니다.** Spring · JPA · HTTP · 외부 API를 모릅니다.
4. **의존은 바깥(adapter) → 안쪽(application → domain)** 방향으로만 흐르고, application은 출력 포트(인터페이스)로 DB와 외부 시스템을 씁니다.
5. **모듈 간 의존은 비순환 그래프이며, 허용 목록을 ArchUnit 테스트에 둡니다.**
6. **필요한 패키지만 만듭니다.** 규칙이 없는 기능에는 도메인 모델 · Mapper · 정책 클래스를 만들지 않습니다.

---

## 1. 최상위 패키지 구조의 문제점과 개선

### 1.1 현재 상태

현재 `backend/src/main/java/com/pickong/`에는 `PickongApplication.java`만 있고, 패키지는 이 문서의 이전 버전에서 **설계만** 했습니다.
이전 설계의 최상위 모듈 19개(`auth`, `user`, `social`, `terms`, `media`, `place`, `route`, `savedplace`, `feed`, `collection`, `plan`, `placeimport`, `conversation`, `notification`, `moderation`, `creator`, `settlement`, `payment`, `admin`)와 `common`은 **그대로 유지합니다.**
각 모듈이 소유 테이블과 API를 겹치지 않게 나누고 있고(2장), 의존 그래프에 순환이 없습니다(6.2).

### 1.2 유지하는 결정과 근거

| 결정 | 근거 |
| --- | --- |
| `place`(공용 장소)와 `savedplace`(개인 저장)를 분리 | `places`는 모든 사용자가 공유하는 마스터 데이터이고, `saved_places`는 회원 개인 데이터입니다. 바뀌는 이유와 권한 규칙이 다릅니다 |
| `settlement`를 `creator`에서 분리 | 정산은 구매(`collection`)를 읽고, `collection`은 판매 자격·수수료율 때문에 `creator`를 읽습니다. 정산이 `creator` 안에 있으면 `creator ↔ collection` 순환이 생깁니다 |
| `payment`는 결제·환불 기록만 | PG 연동과 결제 상태는 컬렉션과 무관하게 재사용됩니다 (Plus 구독 등) |
| `terms`를 `user`에서 분리 | 약관은 버전 · 시행일 · 필수 여부라는 독자적인 생명주기가 있고, 가입 전(비회원)에도 조회됩니다 |
| `notification`은 최상위(leaf) 모듈 | 여러 모듈의 이벤트를 받기만 하고, 어떤 모듈도 `notification`에 의존하지 않습니다 |
| 패키지 이름 `placeimport` | `import`는 Java 예약어라 패키지 이름으로 쓸 수 없습니다 |

### 1.3 바꾸는 것

| # | 이전 설계 | 문제 | 개선 |
| --- | --- | --- | --- |
| 1 | `admin` 모듈이 관리자 API를 모두 받고 각 모듈 in-port를 호출 (파사드) | `admin`이 5개 모듈에 의존하고, 크리에이터 승인 규칙을 고치려면 `creator`와 `admin` 두 곳을 열어야 합니다. 파사드 서비스는 위임만 하는 형식적인 계층입니다 | **관리자 API 컨트롤러는 담당 모듈의 `adapter/in/web/admin/`에 둡니다.** `admin`은 관리자 계정 · 인증 · 역할 · 작업 기록만 담당하고, 다른 모듈이 `admin`의 작업 기록 포트를 호출합니다 (의존 방향이 뒤집힘) |
| 2 | `route` 모듈 안에 방문 순서 최적화 알고리즘 | 최적화 규칙은 고정 시간 · 예약 · 체류 시간 같은 **일정 규칙**에 따라 바뀝니다. 길찾기 모듈에 있으면 일정 규칙을 바꿀 때 `route`를 고쳐야 합니다 | **최적화 · 충돌 검증은 `plan/domain/policy`**, `route`는 **외부 길찾기 호출 · 이동 시간 행렬 · 캐시**만 담당 (5.1) |
| 3 | 모듈 내부 `port/in/command`, `port/in/result` | 요청서의 표준 구조와 다르고, 입력·출력 모델이 포트 폴더 안에 섞여 있습니다 | `application/dto`로 통일 (Command · Query · Result) |
| 4 | 모든 테이블에 domain 모델 + JPA 엔티티 + Mapper | 팔로우, 좋아요, 작업 로그처럼 규칙이 없는 테이블까지 3중 구조가 되어 파일만 늘어납니다 | **3단계 기준**으로 분리 여부를 정합니다 (4.2) |
| 5 | 모듈 내부 템플릿에 이벤트 리스너 · 스케줄러 위치가 없음 | 리스너와 스케줄러가 service에 섞이기 쉽습니다 | `adapter/in/event`, `adapter/in/scheduler` 추가. 둘 다 HTTP와 같은 "입력"이므로 in-port만 호출 |
| 6 | 외부 API 어댑터 위치가 `adapter/out/kakao`, `out/pg` 등으로 제각각 | 무엇이 외부 연동인지 한눈에 보이지 않습니다 | `adapter/out/external/<대상>/`으로 통일 |
| 7 | `common/domain`의 범위가 정해지지 않음 | 공통 패키지로 비즈니스 규칙이 모이기 쉽습니다 | **공유 커널을 목록으로 고정**하고, 추가할 때는 팀 전체 리뷰 (4.5) |

### 1.4 새로 만들지 않는 것

| 후보 | 만들지 않는 이유 |
| --- | --- |
| `tag` 모듈 | 태그 API가 전부 보류입니다. 보류를 풀면 별도 모듈로 만듭니다 (부록 A) |
| `comment`, `like` 모듈 | 댓글·좋아요는 게시물의 공개 범위·카운터·삭제와 함께 움직입니다 (2.2). `feed` 모듈 안에 둡니다 |
| `purchase` 모듈 | 구매는 컬렉션 버전·잠금·미리보기 규칙과 떼어 낼 수 없습니다. 분리하면 `collection ↔ purchase` 순환이 생깁니다 (2.2) |
| `shared/` 비즈니스 패키지 | 여러 모듈이 같은 규칙을 쓰면, 그 규칙을 소유한 모듈의 공개 포트로 제공합니다 |

---

## 2. 도메인 책임과 테이블 소유권

### 2.1 모듈별 책임 · 소유 테이블

**소유 = 그 테이블에 쓰기(INSERT·UPDATE·DELETE)를 하고 JPA 엔티티를 가지는 유일한 모듈**입니다.

| 모듈 | 책임 | 소유 테이블 | 하지 않는 것 |
| --- | --- | --- | --- |
| `auth` | 회원 인증, 토큰 발급·로테이션, 소셜 계정 연결, 비밀번호 재설정, Spring Security 설정, WebSocket 인증 | `refresh_tokens`, `user_social_accounts` | 회원 정보 저장 (`users`는 `user` 소유) |
| `user` | 회원 · 프로필 · 상태(정지·탈퇴), 탈퇴 사유, 사용자 설정, 지도 카테고리 설정, 카운터 | `users`, `withdrawal_reasons`, `user_settings`, `user_map_category_settings` | 제재 사유·이력 (`moderation`), 팔로우 (`social`) |
| `social` | 팔로우 · 차단, 관계 판정(팔로워인가, 차단했는가) | `follows`, `user_blocks` | 팔로워 수 저장 (`users.follower_count`는 `user`가 갱신) |
| `terms` | 약관 버전 · 현재 약관 판정 · 약관 본문 · 동의 · 철회 | `terms`, `terms_sections`, `user_terms_agreements` | |
| `media` | 업로드 URL 발급 · 업로드 확인 · 이미지 처리 · 재시도 · 정리 | `media_files` | 어떤 게시물의 사진인지 (`feed_photos`가 참조) |
| `place` | 장소 마스터, 외부 장소 참조, 영업시간, 장소 검색(지도 API), 좌표 장소 제보 | `places`, `place_submissions`, `place_external_refs`, `place_business_hours`, `place_hours_exceptions` | 개인 메모·방문 상태 (`savedplace`). 제보 승인으로 `saved_places`를 만들지 않음 |
| `route` | 외부 길찾기 호출, 이동 시간 · 거리 · 행렬, 캐시 | 없음 | 방문 순서 최적화 (`plan`) |
| `savedplace` | 저장 장소(찜), 방문 상태, 메모, 저장 출처, 개인 사진 | `saved_places` (+ 개인 사진 테이블, 8.1 #4) | 컬렉션 구성 (`collection`) |
| `feed` | 게시물 · 사진 · 장소 태그, 댓글, 좋아요, 북마크, 피드 | `feeds`, `feed_photos`, `feed_places`, `feed_likes`, `feed_bookmarks`, `comments`, `comment_likes` | |
| `collection` | 컬렉션 · 구성 장소, 유료 가격, 버전 스냅샷, 구매 권한, 열람 기록, 환불 가능 판정, 정산 대상 판정 | `collections`, `collection_places`, `collection_versions`, `collection_version_places`, `collection_purchases` | PG 호출, 결제·환불 기록 (`payment`) |
| `plan` | 일정 · 일정 장소 · 예약, 방문 처리, **방문 순서 최적화 · 일정 충돌 검증**, 날씨 | `plans`, `plan_items`, `plan_item_reservations` | 외부 길찾기 호출 (`route`) |
| `placeimport` | 링크 수집 → 본문 분석 → LLM 추출 → 장소 검증 작업, 진행 상태, 비용 기록 | `import_jobs`, `import_extraction_runs` | 장소 저장 (`savedplace` 일괄 저장 API) |
| `conversation` | 1:1 · 그룹 대화방, 참여자, 메시지, 읽음, 실시간 전달 | `conversations`, `conversation_members`, `messages` | |
| `notification` | 앱 안 알림 생성 · 조회 · 읽음 | `notifications` | 무엇을 알릴지 결정하는 업무 규칙 (각 모듈의 이벤트) |
| `moderation` | 신고 접수·처리, 회원 제재·해제 | `reports`, `user_sanctions` | 회원 상태 저장 (`user`가 `users.status` 갱신) |
| `creator` | 크리에이터 신청·승인·박탈, 등급·수수료율, 정산 계좌 | `creators`, `creator_grades`, `creator_status_histories`, `creator_payout_accounts` | 정산 계산 (`settlement`) |
| `settlement` | 월 정산 집계 · 확정 · 보류 · 지급 상태 | `creator_settlements` | 정산 대상 구매 판정 (`collection`) |
| `payment` | 결제 준비 · 승인 · 실패, PG 연동, 환불 실행 · 기록, 결제 대사 | `payments`, `refunds` | 구매 권한 부여, 환불 가능 판정 (`collection`) |
| `admin` | 관리자 계정 · 로그인 · 역할, 관리자 필터 체인, 관리자 작업 기록 | `admins`, `admin_action_logs` | 신고 처리 · 승인 · 정산 확정 같은 업무 규칙 (담당 모듈) |

### 2.2 경계 결정

| 질문 | 결정 | 근거 |
| --- | --- | --- |
| Comment · Like · Bookmark를 `feed` 안에 둘까 | **`feed` 안에 둡니다.** 단, 게시물 애그리거트와 **별도 애그리거트**(Comment, 좋아요·북마크는 관계 테이블)로 다룹니다 | 셋 다 게시물의 공개 범위 · 차단 · 삭제 규칙을 그대로 따르고, 카운터(`like_count`, `comment_count`, `bookmark_count`)가 `feeds`에 있습니다. 분리하면 게시물 볼 권한 판정을 다른 모듈에 공개해야 합니다. 애그리거트를 나누는 이유는 댓글 하나 쓸 때 게시물 전체를 잠그지 않기 위해서입니다 |
| Purchase · CollectionVersion을 `collection` 안에 둘까 | **`collection` 안에 둡니다** | 잠금·미리보기 판정에는 구매 여부가 필요하고, 구매에는 가격·버전이 필요합니다. 나누면 서로 의존하는 순환이 생깁니다. "구매한 사람만 전체를 본다"는 규칙 하나가 한 모듈에 있어야 고치기 쉽습니다 |
| Refund를 `payment`에서 관리할까 | **기록과 PG 취소는 `payment`, 환불 가능 여부 판정은 `collection`** | `refunds`는 결제(`payment_id`)의 취소 기록이고 PG 호출과 함께 움직입니다. 반면 "첫 열람 전 · 기한 내 · 정산 전"은 컬렉션 구매 규칙입니다. 다른 결제 목적이 생기면 환불 조건도 목적마다 다릅니다 |
| 팔로우 · 차단을 `social`에 둘까 | **`social`에 둡니다** | 차단하면 팔로우를 함께 끊어야 하므로 한 트랜잭션·한 모듈이 맞습니다. 게시물·DM·검색이 모두 "관계 판정"을 쓰므로 하위 모듈로 둡니다 |
| 지도 카테고리 설정 · 사용자 설정을 `user`에 둘까 | **`user`에 둡니다** | 둘 다 회원 1명에게 속한 개인 설정이고, 다른 데이터와 규칙을 공유하지 않습니다. 별도 모듈로 만들 만큼 규칙이 없습니다 |
| Tag를 별도 도메인으로 둘까 | **지금은 만들지 않고, 보류를 풀면 별도 `tag` 모듈** | 태그는 게시물 · 장소 · 사용자 통계 세 곳에 걸쳐 있어 어느 한 모듈에 넣으면 그 모듈이 다른 모듈 데이터를 집계하게 됩니다 |
| 관리자 API | **업무 규칙과 컨트롤러는 담당 모듈, `admin`은 인증 · 역할 · 작업 기록** | 1.3 #1 |
| `/me/**` API | 경로가 아니라 **반환 데이터의 소유자**로 배정 | `/me/purchases` → `collection`, `/me/payments` → `payment`, `/me/creator/settlements` → `settlement`, `/me/bookmarked-feeds` → `feed` |
| `/users/{username}/**` API | 마찬가지로 반환 데이터 기준 | `/feeds` → `feed`, `/collections` → `collection`, `/plans` → `plan`, `/saved-places` → `savedplace` |
| `GET /auth/username-availability` | **`user`** | `users.username`의 규칙과 UNIQUE는 `user`가 소유합니다 (`UsernameController`) |

### 2.3 다른 모듈의 요청으로 바뀌는 컬럼

소유 모듈이 아닌 곳에서 값을 바꿔야 하는 컬럼은 **소유 모듈의 공개 포트를 통해서만** 바꿉니다.

| 컬럼 | 소유 | 바꾸게 하는 모듈 | 방법 |
| --- | --- | --- | --- |
| `users.follower_count`, `following_count` | user | social | `UserCounterUseCase` (같은 트랜잭션, 원자적 증감) |
| `users.feed_count` | user | feed | `UserCounterUseCase` |
| `users.status`, `suspended_until` | user | moderation | `UserStatusUseCase.suspend / ban / lift` |
| `users.password_hash`, `last_login_at` | user | auth | `UserAccountUseCase` |
| `saved_places.visit_status`, `visited_at` | savedplace | plan (방문 기록) | `SavedPlaceCommandUseCase.markVisited` |
| `collection_places` 행 삭제, `collections.place_count` | collection | savedplace (저장 장소 삭제) | 동기 이벤트 `SavedPlaceDeletingEvent` (6.3) |
| `collection_purchases` 생성 · `status` | collection | payment (승인·환불 완료) | `PaymentPurposeHandler` SPI (5.2) |
| `collection_purchases.settlement_id` | collection | settlement | `PurchaseSettlementUseCase.linkSettlement` |
| `admin_action_logs` | admin | moderation, creator, settlement | `AdminActionLogUseCase.record` (같은 트랜잭션) |

### 2.4 보류 기능의 테이블

`tags`, `feed_tags`, `place_tags`, `user_tag_stats`, `user_devices`는 **지금 어떤 모듈도 소유하지 않습니다.** Flyway 초기 스크립트에 넣을지는 8.7에서 정합니다. 보류를 풀 때의 위치는 부록 A에 있습니다.

---

## 3. API ↔ 도메인 매핑

> 경로의 `/api/v1`은 생략했습니다. **보류 API는 빠져 있습니다.**
> "입력 포트"는 컨트롤러가 호출하는 in-port와 메서드입니다. 이름 규칙은 4.3.

### auth
| 메서드 | 경로 | 입력 포트 · 메서드 | 비고 |
| --- | --- | --- | --- |
| GET | `/auth/signup` | `SignUpUseCase.getSession` | 임시 가입 쿠키의 소셜 계정 정보 (온보딩 기본값) |
| POST | `/auth/signup` | `SignUpUseCase.signUp` + `TokenUseCase.issue` | 회원 생성(user) → 소셜 계정 연결 → 약관 동의(terms), 한 트랜잭션 → 세션 쿠키 |
| DELETE | `/auth/signup` | 컨트롤러만 (가입 쿠키 삭제) | 서버에 남는 것 없음 |
| GET | `/auth/oauth2/authorization/{kakao\|google}` → 콜백 `/auth/oauth2/callback/{provider}` | Spring Security → `OAuth2LoginSuccessHandler` → `LoginUseCase.loginWithSocial` + `TokenUseCase.issue` | 컨트롤러 없음 |
| POST | `/auth/refresh` | `TokenUseCase.refresh` | 로테이션 · 재사용 감지 |
| POST | `/auth/logout` | `TokenUseCase.logout` | |
| GET | `/me/social-accounts` | `SocialAccountUseCase.list` | 연결 순서대로, 가입 수단은 `canUnlink=false` |
| POST | `/me/social-accounts/{kakao\|google}` | `SocialAccountUseCase.startLink` | 연결 토큰(5분)이 붙은 시작 주소 `{authorizationUrl}` |
| (콜백) | `/auth/oauth2/authorization/{provider}?link=` → 콜백 | `LinkAwareAuthorizationRequestResolver` → `OAuth2LoginSuccessHandler` → `SocialAccountUseCase.link` | 결과는 `/settings/login-methods?linked=` · `?error=` |
| DELETE | `/me/social-accounts/{kakao\|google}` | `SocialAccountUseCase.unlink` | 찜콩에서만 해제, 가입 수단은 409 |
| POST | `/me/withdrawal` | `AccountWithdrawalUseCase.requestWithdrawal`(→ user `UserWithdrawalUseCase`) + `TokenUseCase.revokeAllSessions` | 탈퇴 신청. 모든 기기 세션 폐기, 쿠키 삭제, 204 |
| GET | `/auth/withdrawal` | `AccountWithdrawalUseCase.getRestoreSession` | 탈퇴 취소 쿠키의 탈퇴 상태 `{username, requestedAt, anonymizeAt}` |
| POST | `/auth/withdrawal/cancel` | `AccountWithdrawalUseCase.cancelWithdrawal` + `TokenUseCase.issue` | 탈퇴 취소 → ACTIVE, 세션 발급 |
| DELETE | `/auth/withdrawal` | 컨트롤러만 (탈퇴 취소 쿠키 삭제) | 탈퇴는 그대로 |
| WS | `/ws` (CONNECT) | `auth/adapter/in/websocket` 인증 인터셉터 | 5.4 |

> **소셜 로그인 스키마 결정 (V1)**
> - 로그인 수단은 카카오 · Google만. `user_social_accounts`의 `(provider, provider_id)` UNIQUE(같은 소셜 계정은 한 찜콩 계정에만), `(user_id, provider)` UNIQUE(제공자마다 하나만 연결).
> - 이메일 1인 1계정: 제공자가 준 **인증된** 이메일만 저장합니다 (가입 계정 → `users.email`, 연결 계정마다 → `user_social_accounts.email`, ERD에 컬럼 추가). 가입 · 연결하려는 소셜 계정의 이메일이 다른 회원의 이메일과 겹치면 "이미 가입된 이메일"로 막고 자동 연결은 하지 않습니다. `users.email`은 대소문자 무시 UNIQUE로 DB가 보장하고, 연결 계정 이메일은 같은 회원의 카카오 · Google 이메일이 같을 수 있어 서비스에서 확인합니다. 카카오는 이메일을 주지 않을 수 있어 확실한 1인 1계정은 이후 휴대폰 본인확인으로 보장합니다.
> - 아이디(`username`)는 대소문자 무시 UNIQUE, 이름(`display_name`)은 NULL 허용. 회원 행은 온보딩을 마칠 때 만들어지므로 온보딩 완료 여부 컬럼은 두지 않습니다.

> **보안 결정 (2단계)**
> - **Access Token:** JWT · HS256 · 15분, 쿠키 `pickong_at`. 내용은 `iss=pickong`, `sub=회원 id`, `iat`, `exp`만 (개인정보 없음). 비밀값은 환경변수 `JWT_SECRET` 또는 `backend/.env`, 기본값 없음(없으면 시작 실패).
>   - 백엔드와 **별개인 서버**(예: 분리된 WebSocket 서버, 다른 서비스)가 토큰을 직접 검증하게 되면 비밀값을 나눠 주지 않도록 **RS256(공개키 검증)으로 바꿉니다.** Refresh Token은 DB에 있는 별개 값이라 바꿔도 사용자는 로그아웃되지 않습니다.
> - **인증 필터:** `JwtCookieAuthenticationFilter`가 쿠키 서명·만료와 `UserAccountUseCase.canLogin`의 현재 ACTIVE 상태를 검사하고, 실패하면 비로그인으로 통과시킵니다. 로그인이 필요한 경로는 인가 단계가 401로 막고, 공개 경로(재발급 등)는 만료된 쿠키가 있어도 동작해야 하기 때문입니다. (Resource Server 필터는 잘못된 토큰이면 공개 경로에서도 401을 내서 쓰지 않음)
> - **세션 없음(STATELESS)**, 폼 로그인 · Basic · 기본 로그아웃 끔. 401 · 403은 공통 오류 형식 JSON.
> - **CSRF:** 쿠키 SameSite=Lax + Spring CSRF 토큰(SPA 방식: `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더). 토큰 오류는 403 `INVALID_CSRF_TOKEN`으로 권한 오류와 구분합니다. GET으로 상태를 바꾸지 않습니다.
> - **경로 기준:** DispatcherServlet이 `/api/v1`이라 Spring Boot가 보안 경로 패턴의 basePath를 `/api/v1`로 잡습니다. 보안 설정의 경로는 `/api/v1`을 빼고 적습니다.
> - **`@CurrentUser LoginUser`**(common/security) + `CurrentUserArgumentResolver`(common/web). `required = false`면 비로그인 시 null.

> **Refresh Token 결정 (3단계)**
> - 랜덤 256비트 문자열(JWT 아님), DB에는 SHA-256 해시만. 수명 14일, 재발급할 때마다 '지금 + 14일'로 연장(슬라이딩).
> - **로테이션 · 재사용 감지:** 재발급하면 쓴 토큰을 폐기(`revoked_at`)하고 같은 family로 새 토큰 발급. 폐기된 토큰이 다시 오면 family 전체 폐기. 폐기는 조건부 UPDATE(`WHERE revoked_at IS NULL`)라 같은 토큰으로 동시에 와도 한 요청만 성공합니다. 별도 '로테이션됨' 컬럼 없이 `revoked_at` 하나로 판단합니다.
> - 세션 폐기 후 401을 던질 때 폐기가 롤백되지 않도록 `refresh`는 `@Transactional(noRollbackFor = BusinessException.class)`.
> - **동시 재발급:** 서버는 엄격(유예 시간 없음). 여러 탭이 같은 토큰으로 동시에 재발급하지 않도록 프론트가 Web Locks API로 줄을 세웁니다(프론트 연동 단계).
> - **발급·재발급 시 회원 상태 확인:** `UserAccountUseCase.canLoginForUpdate`로 사용자 행을 먼저 잠그고 만료 정지를 정리합니다. ACTIVE가 아니면 발급을 거절하며 재발급 시 남은 모든 기기의 토큰을 폐기하고 401을 반환합니다.
> - **로그아웃:** 이 기기(family)만 폐기. 모든 기기 로그아웃은 필요해지면 추가.
> - **쿠키:** `pickong_at`(Path=/, Max-Age 15분), `pickong_rt`(Path=/api/v1/auth, Max-Age 14일), 모두 HttpOnly · SameSite=Lax. Secure(`COOKIE_SECURE`, 기본 true) · Domain(`COOKIE_DOMAIN`, 기본 비움)은 설정값.
> - **만료 토큰 정리:** `auth/adapter/in/scheduler/ExpiredTokenCleanupScheduler`가 매일 04:00(한국 시간)에 `TokenUseCase.deleteExpiredTokens`를 호출해, 만료된 지 7일(`cleanup-retention`)이 지난 행을 폐기 여부와 관계없이 지웁니다. 만료 전의 폐기된 토큰은 남기므로 재사용 감지는 만료까지 동작합니다. 같은 결과가 나오는 작업이라 분산 잠금은 쓰지 않습니다. `@EnableScheduling`은 `common/config/SchedulingConfig`.

> **소셜 로그인 결정 (4단계)**
> - Spring Security OAuth2 Client(`spring-boot-starter-security-oauth2-client`). 카카오는 provider 주소를 직접 등록, Google은 기본 등록(OIDC, `openid` scope로 ID 토큰 · nonce 검증).
> - 경로: 시작 `/api/v1/auth/oauth2/authorization/{provider}`, 콜백 `/api/v1/auth/oauth2/callback/{provider}`. **OAuth2 필터 matcher에는 Boot basePath가 붙지 않아 전체 경로로 설정**(실제 서버 TRACE 로그로 확인).
> - Redirect URI는 `FRONTEND_BASE_URL` 기준(프론트 도메인으로 받아 Next가 전달 → 쿠키가 프론트 도메인에 저장). Spring 7은 콜백 URL과 등록 주소의 일치가 아니라 state만 비교하므로 백엔드가 Next 뒤에 있어도 동작.
> - **OAuth state:** 세션 대신 서명된 쿠키(`pickong_oauth`, 10분, `SignedCookieAuthorizationRequestRepository`). 콜백의 state와 쿠키의 state가 같을 때만 진행, 한 번 쓰면 삭제.
> - **서명 토큰 종류 구분:** Access · 임시 가입 · OAuth state를 모두 같은 키(HS256)로 서명하므로 `token_type` 클레임으로 구분하고(`JwtCodec`), 읽을 때 기대한 종류가 아니면 거절. (임시 가입 토큰의 sub는 카카오 회원번호라, 구분하지 않으면 Access Token으로 통과할 수 있음)
> - **판단(`LoginService`)과 세션 발급(`TokenService`)을 분리**하고 OAuth2 성공 처리(`OAuth2LoginSuccessHandler`)가 이어 붙임. 서비스끼리 호출하지 않는 규칙(4.3) 때문.
> - 임시 가입 세션: 서명된 쿠키 토큰(`pickong_signup`, 30분, Path=/api/v1/auth). 회원 행은 온보딩 완료(5단계) 때 생성.
> - 이메일: 제공자가 인증한 것만(카카오 `is_email_valid` · `is_email_verified`, Google `email_verified`), 소문자로 저장 · 비교. 신규 소셜 계정의 이메일이 이미 연결된 소셜 계정 이메일과 같으면 가입 차단(자동 연결 안 함).
> - 제공자의 Access Token은 저장하지 않음(`NoOpAuthorizedClientRepository`). 프로필 사진은 제공자 URL을 https로 바꿔 그대로 사용(업로드 기능 생기면 복사 검토).

> **온보딩 결정 (5단계)**
> - 이메일 · 비밀번호 로그인과 비밀번호 재설정은 소셜 로그인 전환으로 없앴습니다(위 표에서 제거). `password_reset_tokens` 엔티티는 남아 있지만 V1에 테이블이 없습니다.
> - 가입 흐름은 `auth.SignUpService`가 맡고, 회원 생성은 `user`의 `UserAccountUseCase.register`, 약관 동의는 `terms`의 `TermsUseCase.agreeOnSignup`으로 처리합니다. 회원 생성 · 소셜 계정 연결 · 약관 동의가 **한 트랜잭션**이라 하나라도 실패하면 아무것도 남지 않습니다. 세션 발급은 커밋 뒤 컨트롤러가 `TokenUseCase.issue`로.
> - **아이디 규칙은 `user.domain.vo.Username`**: 영문으로 시작 3~20자, 영문 소문자 · 숫자 · `_` · `.`, 끝 · 연속 마침표 금지, 소문자로 저장. 예약어(admin · pickong · official 등)는 '사용 중'. 이름은 `User.register`에서 공백 정리 · 비면 null · 20자(코드 포인트) 초과 금지.
> - **약관 초기 데이터는 Flyway `V2__seed_terms.sql`**(SERVICE · PRIVACY 필수, MARKETING 선택, 1.0). SERVICE · PRIVACY **1.1**은 `V12__add_terms_v1_1.sql`(2026-11-25 시행, 탈퇴 · 재가입 · 파기 조항, `requires_reconsent = false`라 1.0 동의를 그대로 인정). 필수 여부는 DB(`is_required`)를 따르고, 종류마다 시행일이 지난 최신 버전으로 동의를 기록. 약관을 바꿀 때는 새 버전 행을 넣는 새 마이그레이션 추가. **버전 행과 그 조항(`terms_sections`)은 같은 마이그레이션에 넣습니다** (조항 없이 버전만 넣으면 시행일부터 전문 조회가 빈 본문으로 200을 줌).
> - **동시 가입:** 서비스가 먼저 확인해 정확한 사유를 알려 주고, 동시에 사전 확인을 통과한 경우는 DB UNIQUE가 막습니다. 어댑터가 `saveAndFlush` 후 제약 이름(`uk_users_username_lower` · `uk_users_email_lower` · `uk_user_social_accounts_provider_id`)으로 `USERNAME_TAKEN` · `EMAIL_ALREADY_REGISTERED` · `SOCIAL_ACCOUNT_ALREADY_LINKED`로 바꿉니다.
> - 사진은 클라이언트가 주소를 보내지 않고 `useSocialProfileImage` 여부만 보내며, 서버는 임시 가입 토큰에 있는 주소만 씁니다(임의 URL 저장 방지).

> **로그인 수단 연결 결정 (6단계)**
> - 카카오 · Google을 한 계정에 함께 연결할 수 있고(제공자마다 하나), **가입할 때 쓴 수단은 해제할 수 없습니다.** 가입 수단 = 가장 먼저 연결된 소셜 계정(id가 가장 작은 행). 그 행은 지울 수 없어 계속 첫 번째로 남으므로 DB 컬럼 없이 판별하고, '마지막 남은 수단 해제'도 저절로 생기지 않습니다.
>   - 검토한 다른 안: 연결 없음(복구 수단 없음), 자체 로그인(비밀번호 · 재설정 메일/SMS · 무차별 대입 방어가 필요하고 소셜 전용 정책이 바뀜), 카카오톡 메시지로 재설정(카카오를 잃으면 받을 수 없음). 소셜과 무관한 복구가 필요해지면 휴대폰 본인확인을 붙입니다.
> - **연결 시작:** CSRF 보호된 `POST /me/social-accounts/{provider}` → 서명 연결 토큰(`token_type=link`, sub=회원 id, provider, 5분)을 붙인 시작 주소. 시작할 때 `LinkAwareAuthorizationRequestResolver`가 ① 토큰이 유효하고 같은 제공자용인지 ② 로그인 쿠키(`pickong_at`)의 회원과 같은지 확인합니다. 둘 다 맞아야 회원 id를 OAuth 시작 정보에 넣어 서명된 state 쿠키로 콜백까지 가져갑니다. 다른 사이트가 링크로 연결을 시작시키거나, 새어 나간 주소를 남이 쓰는 것을 막습니다. 토큰은 1회용 기록 없이 짧은 만료로만 제한(로그인 쿠키가 함께 있어야 쓸 수 있어서).
>   - OAuth 시작 필터가 JWT 쿠키 필터보다 앞에 있어 resolver가 쿠키를 직접 검증합니다. 시작 단계 예외는 Spring 기본값이 500이라, 시작 필터에도 `OAuth2LoginFailureHandler`를 연결했습니다(`withObjectPostProcessor`). 그래서 모르는 제공자로 시작해도 이제 `/login?error=OAUTH_FAILED`.
> - 연결할 때만 `prompt=select_account`로 제공자의 계정 선택 화면을 띄웁니다(브라우저에 다른 사람 계정이 로그인돼 있어도 모르고 연결하지 않게). 일반 로그인은 그대로.
> - **콜백 규칙(`SocialAccountService.link`):** 회원 상태(ACCOUNT_UNAVAILABLE) → 이미 내 계정에 연결된 같은 계정이면 그대로 성공 → 남의 계정이면 SOCIAL_ACCOUNT_ALREADY_LINKED → 같은 제공자의 다른 계정이 있으면 PROVIDER_ALREADY_LINKED → 인증된 이메일이 다른 회원의 소셜 계정에 있으면 EMAIL_ALREADY_REGISTERED. 동시 요청은 DB UNIQUE가 막고 어댑터가 제약 이름(`uk_user_social_accounts_user_provider` 추가)으로 같은 코드로 바꿉니다. 로그인 세션 쿠키는 건드리지 않습니다.
> - 해제는 찜콩 DB에서만(카카오 unlink · Google revoke는 호출하지 않음, 제공자 토큰을 저장하지 않으므로). 탈퇴할 때도 같습니다(아래 '회원 탈퇴 결정').

> **내 정보 · 프로필 수정 결정 (7단계)**
> - **Flyway V3:** `users.region VARCHAR(20)`(활동 지역), `users.username_changed_at TIMESTAMPTZ`(마지막 아이디 변경 시각) 추가 (ERD 반영 필요).
> - **아이디는 14일에 한 번.** 가입 후 첫 변경은 바로 가능(온보딩 오타 수정). 지금과 같은 아이디를 보내면 변경으로 치지 않습니다(프로필 편집은 아이디를 늘 함께 보냄). 다음 변경 가능 시각은 `GET /me`의 `usernameChangeableAt`(지금 가능하면 null). 예전 아이디 보호(다른 사람이 바로 가져가는 것 방지)는 하지 않았고, 필요해지면 아이디 이력 테이블로 추가합니다.
> - **PATCH는 보낸 항목만 변경**, `null`은 지우기(아이디는 지울 수 없어 400). Jackson 3은 record · Optional로 '없음'과 'null'을 구분하지 못해, 요청 DTO는 setter가 불린 항목만 기록합니다. 한 항목이라도 틀리면 아무것도 저장하지 않습니다.
> - 규칙은 domain(`User.changeUsername` · `rename` · `changeBio` · `changeRegion`): 앞뒤 공백 정리 → 비면 null → 이름 20 · 소개 150 · 지역 20자(코드 포인트). 사용 중 · 예약어 확인은 `UserProfileService`.
> - **저장은 프로필 컬럼만 UPDATE**(`UserJpaRepository.updateProfile`). 엔티티 전체를 저장하면 같은 순간 다른 모듈이 바꾼 `follower_count` · `feed_count`를 옛 값으로 덮을 수 있어서입니다.
> - 응답의 `id`는 `users.public_id`(UUID, 순번 노출 방지). `postCount` · `followerCount` · `followingCount`는 users의 집계 컬럼(피드 · 팔로우 기능 전까지 0), `isCreator`는 크리에이터 기능 전까지 false.
> - `isUsernameAvailable`은 `UserAccountService`(auth 전용)에서 `UserProfileService`로 옮겼습니다(Query · Command 유스케이스를 함께 구현).

> **회원 탈퇴 결정 (user · auth 구현 완료, feed 쪽은 #103)**
> - **30일 유예 후 익명화.** `POST /me/withdrawal` → `WITHDRAWN_PENDING`: 바로 모든 세션을 폐기하고, 프로필 · 게시물이 남에게 보이지 않습니다. 30일이 지나면 예약 작업(`user/adapter/in/scheduler`)이 익명화해 `WITHDRAWN`으로 바꿉니다.
>   - 익명화: 이름 · 이메일 · 프로필 사진 · 소개 · 활동 지역을 지우고, 소셜 연결(`user_social_accounts`)을 삭제하고, 아이디를 내부 값(예: `deleted_{id}`)으로 바꿉니다. **원래 아이디는 다른 사람이 다시 쓸 수 있습니다.** `users` 행은 남깁니다(댓글 · 신고 등이 FK로 가리킴).
>   - 검토한 다른 안: 즉시 익명화(실수로 탈퇴해도 복구 불가, 제재 회피 재가입을 못 막음), 유예하되 복구 없음(회원에게 유예의 이점이 없음).
> - **탈퇴 취소:** 유예 기간에 같은 소셜 계정으로 로그인하면 새 가입이 아니라 탈퇴 취소 화면으로 보냅니다. 취소하면 `ACTIVE`로 돌아오고 숨겼던 게시물이 다시 보입니다. 취소 흐름은 아래 '회원 탈퇴 구현'.
> - **재가입:** 유예 기간에는 소셜 연결이 남아 있어 같은 계정으로 새로 가입할 수 없습니다(로그인하면 탈퇴 취소로). 익명화한 뒤에는 연결이 지워져 같은 소셜 계정으로 새 회원으로 가입할 수 있습니다. 소셜 식별자를 따로 보관해 막는 방식은 제재 기능(moderation)이 생길 때 검토합니다.
> - **게시물 · 댓글:** 게시물은 유예 기간에 숨기고 익명화할 때 삭제(`deleted_at`). 댓글은 남기고 작성자를 "탈퇴한 사용자"로 보여 줍니다(다른 사람의 대화 흐름 유지).
>   - **숨김은 작성자 상태로 합니다.** feed의 게시물 조회는 작성자(`users.status`)가 `ACTIVE`가 아닌 게시물을 빼고 보여 줍니다(`WITHDRAWN_PENDING` · `WITHDRAWN` 제외). 게시물 데이터를 바꾸지 않으므로 탈퇴를 취소하면 바로 다시 보이고, 숨김 · 해제 이벤트가 필요 없어 이벤트가 유실돼도 어긋나지 않습니다. 댓글의 "탈퇴한 사용자" 표시도 같은 기준입니다.
>   - 검토한 다른 안: `feeds.hidden_at`(신고 숨김과 섞여 탈퇴를 취소할 때 신고로 숨긴 글까지 다시 보임), 별도 컬럼 `withdrawal_hidden_at`(마이그레이션과 숨김 · 해제 이벤트가 필요하고, 이벤트가 유실되면 어긋남), `hidden_at` + `hidden_reason`(신고로 숨긴 글의 작성자가 탈퇴하면 사유를 하나밖에 담지 못해 같은 문제가 남음).
> - **각 모듈의 회원 데이터:** 회원이 만든 개인 데이터(게시물 · 저장 장소 · 컬렉션 · 일정 등)는 각 모듈이 익명화 이벤트(`UserWithdrawnEvent`)를 받아 지웁니다. 남의 화면에 남는 기록(댓글 · 신고 · 장소 제보)은 지우지 않습니다.
>   - **정리는 익명화와 같은 트랜잭션의 동기 이벤트(`@EventListener`)로 합니다.** 한 모듈이라도 실패하면 익명화까지 되돌아가 회원이 `WITHDRAWN_PENDING`으로 남고, 다음 날 예약 작업이 그 회원을 다시 집어 처음부터 처리합니다(회원마다 트랜잭션을 나눠 한 명의 실패가 다른 회원을 막지 않음). 그래서 '익명화됐는데 데이터가 남음' 상태가 생기지 않고 모듈별 재처리 작업이 필요 없습니다. 실패한 회원은 로그에 남깁니다.
>   - 검토한 다른 안: 커밋 후 이벤트 + 모듈별 재처리 작업(`status = WITHDRAWN`인데 남은 데이터를 찾아 다시 지움). 모듈 오류가 익명화를 막지 않지만, 모듈마다 재처리 작업이 필요하고 그동안 데이터가 남습니다.
>   - 구독하는 모듈은 각 리스너가 여러 번 불려도 결과가 같게(이미 지운 것은 건너뛰게) 만듭니다.
> - **소셜 제공자:** 찜콩 DB에서만 연결을 지웁니다(카카오 unlink · Google revoke 호출 안 함). 카카오 · Google 계정 설정의 '연결된 앱'은 회원이 직접 끊습니다.
> - **탈퇴 사유:** 사유와 기타 입력, 날짜만 저장하고 회원 id는 남기지 않습니다(통계용, 익명화 뒤에도 개인과 연결되지 않게). `withdrawal_reasons`(V11)는 **시각 대신 날짜(`requested_on DATE`)**만 둡니다. 정확한 시각을 남기면 `users.withdrawal_requested_at`과 맞춰 보고 누구의 사유인지 알아낼 수 있기 때문입니다.
> - **안내 문구:** 결제 기록 보관 · 크리에이터 정산 안내는 그 기능을 만들 때 약관과 함께 넣습니다. 탈퇴 · 재가입 · 파기 조항은 약관 1.1에 넣습니다(먼저 새 버전 재동의 #99).
>
> **회원 탈퇴 구현 (user · auth)**
> - **탈퇴 신청 `POST /me/withdrawal {reason, detail}`:** 컨트롤러는 auth(`WithdrawalController`)에 있습니다. 회원 상태는 user가 바꾸지만, 세션 폐기 · 쿠키 삭제는 auth가 해야 해서입니다(가입 완료와 같은 방식). 순서: `UserWithdrawalUseCase.requestWithdrawal`(사유 확인 → `WITHDRAWN_PENDING` → 사유 저장, 한 트랜잭션) → 커밋 뒤 `TokenUseCase.revokeAllSessions`(모든 기기) → 이 브라우저의 쿠키 삭제 → 204.
>   - 세션 폐기를 이벤트(`UserWithdrawalRequestedEvent`) 대신 직접 부릅니다. 같은 요청 안에서 쿠키도 지워야 해서 어차피 auth가 이어 받기 때문입니다. 폐기가 혹시 실패해도 탈퇴 중인 회원은 재발급이 거절됩니다(`canLogin`). Access Token도 매 요청 ACTIVE 여부를 검사하므로 탈퇴 중 재탈퇴 요청은 401입니다. 복구 쿠키를 이용하는 탈퇴 취소는 유지합니다.
>   - 본문이 있는 요청이라 `DELETE /me` 대신 `POST`로 했습니다. DELETE 본문은 HTTP에서 의미가 정해져 있지 않아 버려질 수 있고, 바로 지우는 것이 아니라 '신청'이기 때문입니다.
>   - 사유: `RARELY_USED` · `MISSING_FEATURE` · `PRIVACY` · `OTHER_SERVICE` · `ETC`(대소문자 무관). ETC는 내용 필수, 내용은 300자(프론트 입력 제한과 같음). 틀리면 400 `INVALID_WITHDRAWAL_REASON`, 비ACTIVE 계정의 Access Token은 인증 단계에서 401로 거절합니다. 인증 이후 요청 중 계정 상태가 바뀌면 도메인 검증에서 409 `WITHDRAWAL_NOT_ALLOWED`가 가능합니다.
> - **탈퇴 취소:** 유예 중 회원이 소셜 로그인하면(`LoginService`) 로그인 세션 대신 **탈퇴 취소 토큰**(서명 JWT `token_type=withdrawal_restore`, 10분, 쿠키 `pickong_restore`, Path=/api/v1/auth)을 주고 `/account/restore`로 보냅니다. 탈퇴 중인 계정에 로그인 세션을 만들지 않고, 로그인 · 재발급 규칙(`canLogin`: ACTIVE만)을 바꾸지 않으려는 것입니다. 가입 토큰과 같은 방식입니다.
>   - `GET /auth/withdrawal`(탈퇴 상태) · `POST /auth/withdrawal/cancel`(취소 → ACTIVE, 세션 발급, 취소 쿠키 삭제) · `DELETE /auth/withdrawal`(그만두기, 쿠키만 삭제). 쿠키가 없거나 만료면 401 `RESTORE_SESSION_EXPIRED`.
>   - **30일이 지나면 익명화 전이라도 취소할 수 없습니다**(409 `WITHDRAWAL_NOT_CANCELLABLE`, 로그인은 `ACCOUNT_UNAVAILABLE`). 예약 작업이 하루 한 번이라 생기는 틈에 취소되면 정책(30일)과 어긋나기 때문입니다.
> - **익명화 예약 작업:** `user/adapter/in/scheduler/WithdrawalAnonymizationScheduler`, 매일 04:30(한국 시간, `pickong.user.withdrawal.anonymize-cron`). 만료 토큰 정리(04:00)와 겹치지 않게 30분 뒤입니다. 대상 id를 먼저 읽고 **회원마다 트랜잭션**(`UserWithdrawalUseCase.anonymize`)으로 처리하며, 그 안에서 다시 읽어 지금도 대상인지 확인합니다. 실패한 회원은 로그(error)에 남기고 다음 회원으로 넘어갑니다.
>   - 순서: `UserWithdrawnEvent` 동기 발행(auth `UserWithdrawnListener`: 소셜 연결 · Refresh Token 삭제, 이후 feed 등) → 회원 행 익명화(`updateAnonymized`, 개인정보 컬럼만 UPDATE해 집계 컬럼을 덮어쓰지 않음).
>   - 익명화 아이디는 `deleted_{id}`이고, **`deleted_`로 시작하는 아이디는 예약어**(`Username.isReserved`)라 회원이 가져갈 수 없습니다. 겹치면 UNIQUE 위반으로 익명화가 실패하기 때문입니다.

### user
| 메서드 | 경로 | 입력 포트 · 메서드 | 비고 |
| --- | --- | --- | --- |
| GET | `/auth/username-availability` | `UserQueryUseCase.isUsernameAvailable` | 경로는 auth지만 데이터는 user. 대소문자 무시, 예약어는 사용 중 |
| GET | `/me` | `UserQueryUseCase.getMe` | 프론트 UserDto 모양 + `usernameChangeableAt` |
| PATCH | `/me` | `UserCommandUseCase.updateProfile` | 보낸 항목만 변경 (아이디 · 이름 · 소개 · 활동 지역) |
| GET | `/users/{username}` | `UserQueryUseCase.getProfile` | 프론트 UserDto 모양(`usernameChangeableAt` 없음). 대소문자 무시, ACTIVE 회원만(그 외 404 `USER_NOT_FOUND`). `isFollowing`은 `follows` 마이그레이션 전까지 false. 지금은 로그인 필요: 공개로 열면 Access Token이 만료된 채 내 프로필을 열 때 401(재발급)이 오지 않고 `isMe=false`로 보임 |
| GET | `/users/search` | `UserQueryUseCase.search` | 차단 관계 제외 |
| GET · PUT | `/me/settings` | `UserSettingsUseCase.get / update` | |
| GET · PUT | `/me/map-categories` | `UserSettingsUseCase.getMapCategories / replaceMapCategories` | 설정과 같은 포트 |

### social
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| POST · DELETE | `/users/{username}/follow` | `RelationCommandUseCase.follow / unfollow` |
| GET | `/users/{username}/followers`, `/following` | `RelationQueryUseCase.followers / followings` |
| POST · DELETE | `/users/{username}/block` | `RelationCommandUseCase.block / unblock` |
| GET | `/me/blocks` | `RelationQueryUseCase.myBlocks` |

### terms
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| GET | `/terms/{type}` | `TermsUseCase.getCurrentTerms` (로그인 없이, 시행 중인 버전과 조항) |
| GET | `/me/agreements` | `TermsUseCase.getMyAgreements` |
| PATCH | `/me/agreements` | `TermsUseCase.updateAgreement` (선택 약관만 동의 · 철회) |
| GET | `/me/agreements/pending` | `TermsUseCase.getPendingTerms` (재동의 화면: 지금 동의 받을 약관) |
| POST | `/me/agreements` | `TermsUseCase.agreeToPendingTerms` (재동의 화면의 동의를 한 번에) |

> **약관 동의 결정**
> - 가입할 때의 동의는 `POST /auth/signup` 안에서 `TermsUseCase.agreeOnSignup`으로 남깁니다(위 auth 표).
> - `user_terms_agreements`는 동의 · 철회 이력이라 (user_id, terms_id)가 UNIQUE가 아닙니다. 대신 **철회하지 않은 동의는 부분 유니크 인덱스 `uk_user_terms_agreements_active`(V8, `WHERE revoked_at IS NULL`)로 하나만** 둡니다. 동의 요청이 동시에 와서 둘 다 서비스의 '동의 중 아님' 확인을 통과해도 기록은 하나입니다.
> - 회원의 동의는 `INSERT … ON CONFLICT DO NOTHING`(native)으로 저장합니다. 유니크 예외를 잡는 방식은 PostgreSQL 트랜잭션이 중단돼 뒤이은 조회가 실패하므로 쓰지 않습니다.
> - 철회는 행을 지우지 않고 `revoked_at`만 채웁니다. 필수 약관은 동의 · 철회 모두 400(해지는 탈퇴로만).
> - 동의 상태의 버전 · 제목은 **동의한 버전**의 것을 함께 보여 줍니다(새 버전에서 제목이 바뀌어도 섞이지 않게).
>
> **새 버전 재동의 결정 (#99)**
> - **버전마다 DB에서 정합니다(`terms.requires_reconsent`, V10).** `true`면 이 버전이 시행된 뒤 이전 버전에 한 동의를 인정하지 않고, `false`면 공지만 하고 이전 동의를 그대로 인정합니다. 예: 개인정보 수집 항목 · 목적이 바뀌면 `true`, 문구 정리는 `false`. 새 버전을 넣는 마이그레이션에서 고릅니다.
>   - 검토한 다른 안: 항상 재동의(오타만 고쳐도 모두에게 동의 화면), 재동의 없음(개인정보 항목이 바뀔 때 다시 동의를 받을 방법이 없음).
> - **유효 규칙(`terms.domain.model.TermsHistory`):** 그 종류에서 가장 최근에 시행된 '재동의 필요' 버전이 기준입니다. 회원이 동의한 버전이 기준과 같거나 그 뒤에 시행된 것이면 유효합니다. 예: 1.0 → 1.1(필요) → 1.2(필요 없음)이면 1.1 · 1.2 동의는 유효, 1.0 동의는 무효. 문구 정리 버전이 나올 때마다 다시 묻지 않으려고 기준을 '지금 버전'이 아니라 '마지막 재동의 필요 버전'으로 둡니다.
> - **동의 받을 약관(`getPendingTerms`):** 필수 약관은 재동의 필요 버전이 시행됐는데 유효한 동의가 없을 때, 선택 약관은 예전 동의가 남아 있지만 더 이상 유효하지 않을 때(예전에 동의했던 회원에게만 다시 묻기). 재동의 필요 버전이 없는 종류는 동의 기록이 없어도 묻지 않습니다. 그래서 **새 필수 약관 종류를 추가할 때는 `requires_reconsent = true`로 넣어야** 기존 회원에게 동의를 받습니다.
> - **필수 약관 차단:** 동의할 때까지 서비스를 쓸 수 없습니다. 화면뿐 아니라 서버도 막습니다. `auth`의 `TermsConsentFilter`가 인가 단계 뒤에서 로그인한 요청마다 `TermsUseCase.hasPendingRequiredTerms`를 확인하고 403 `TERMS_RECONSENT_REQUIRED`를 줍니다. 열어 둔 요청: `/terms/**` · `/me/agreements/**` · `GET /me` · `POST /me/withdrawal`(탈퇴 신청) · `/auth/**`(로그아웃 · 재발급).
>   - 확인은 요청마다 DB로 합니다(시행된 약관 · 내 동의 조회 두 번). 토큰에 넣으면 새 버전 시행 뒤 최대 15분(Access Token 수명) 동안 막지 못하고, 동의한 뒤 토큰을 다시 발급해야 합니다. 약관 행은 몇 개뿐이라 캐시는 두지 않았습니다.
> - **로그인 직후:** 동의 받을 약관이 있으면 `/feed` 대신 `/terms/consent`로 보냅니다(`OAuth2LoginSuccessHandler`). 선택 약관만 남은 경우에도 한 번 묻습니다.
> - **재동의 저장(`POST /me/agreements {agreedTerms}`):** 가입과 같은 규칙으로 한 트랜잭션에서 처리합니다. 동의 받을 필수 약관이 빠지면 400 `REQUIRED_TERMS_NOT_AGREED`이고 아무것도 저장하지 않습니다. 보낸 종류 중 유효한 동의가 없는 것만 시행 버전으로 기록합니다. 다시 물은 선택 약관을 보내지 않으면 예전 동의를 철회해 다음 로그인부터 묻지 않습니다.
> - 설정의 마케팅 스위치(PATCH)도 같은 규칙입니다. 재동의 필요 버전이 시행된 뒤에는 '동의하지 않음'으로 보이고, 켜면 시행 버전으로 기록합니다.

### media
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| POST | `/media/presigned` | `MediaUploadUseCase.issueUploadUrl` |
| POST | `/media/{mediaId}/confirm` | `MediaUploadUseCase.confirmUpload` |
| DELETE | `/media/{mediaId}` | `MediaUploadUseCase.delete` |

### place
| 메서드 | 경로 | 입력 포트 · 메서드 | 비고 |
| --- | --- | --- | --- |
| POST | `/place-submissions` | `PlaceSubmissionUseCase.submit` | 구현됨. 로그인한 회원의 대기 제보만 만듦. 본문의 회원 id는 쓰지 않음 |
| GET | `/admin/place-submissions` | `PlaceSubmissionUseCase.list` | 구현됨. 배열 전체. `page` · `size` · `totalCount` 없음. 검색 · 집계 · 정렬은 화면 |
| GET | `/admin/place-submissions/{id}` | `PlaceSubmissionUseCase.get` | 구현됨. `{id}`는 DB id 문자열. 승인된 `placeId`는 장소 `public_id` |
| POST | `/admin/place-submissions/{id}/approve` | `PlaceSubmissionUseCase.approve` | 구현됨. 본문 없음. 심사자 id는 인증된 관리자 주체 |
| POST | `/admin/place-submissions/{id}/reject` | `PlaceSubmissionUseCase.reject` | 구현됨. 본문 `{ "reason" }`. 공백만 있으면 거절 |
| GET | `/places?bbox=&category=&cluster=` | `PlaceQueryUseCase.findMap` / `findInBounds` | 구현됨. `places` 행만. bbox(서경,남위,동경,북위)는 필수이고, 없으면 400. 대기 · 반려 제보는 없음. 응답은 `{ places, clusters }`. 아래 '지도 장소 묶기' 참고 |
| POST | `/places` | `PlaceCommandUseCase.createCustomPlace` | 이후 설계. 컨트롤러 없음. 제보 접수가 아님 |
| GET | `/places/{placeId}` | `PlaceQueryUseCase.getDetail` | 이후 설계. 영업시간 포함. 클래스 없음 |
| GET | `/places/{placeId}/summary` | `PlaceQueryUseCase.getSummary` | 이후 설계. 클래스 없음 |
| GET | `/places/search` | `PlaceSearchUseCase.search` | 이후 설계. 클래스 없음 |

**지도 장소 묶기:** `bbox`가 있으면 혼자 있는 장소는 `places`, 묶은 장소는 `clusters`(`lat` · `lng` · `count` · `bounds`)로 나눠 돌려줍니다. 범위 안의 서로 다른 좌표가 200개 이하면 같은 좌표끼리만 묶고, 넘으면 격자로 묶습니다(칸 크기는 `MapGridPolicy`). 지도마다 줌 값이 뜻하는 범위가 달라 줌이 아니라 좌표 수로 정합니다. `category`를 주면 그 카테고리만 셉니다. `cluster=false`는 묶지 않은 목록(이름순, 최대 500곳)이고 `clusters`는 비어 있습니다. `bbox`가 없으면 전체 장소를 한 번에 돌려주지 않도록 400(`INVALID_INPUT`)이고, 가로나 세로 한 변이 16km를 넘으면 400(`BOUNDS_TOO_LARGE`)입니다(`MapBoundsPolicy`. 프론트는 15km를 넘으면 요청하지 않고, 범위를 바깥쪽으로 약 100m씩 넓혀 보내므로 여유를 둡니다). 범위 조회는 `places`의 `location::geometry` GiST 인덱스(V18)를 씁니다. 묶음 개수에는 회원이 이미 저장한 장소도 포함됩니다. SQL은 `PlaceMapQueryAdapter`에 있습니다.

제보 상태 전이는 대기 → 승인 또는 대기 → 반려만입니다. 승인하면 `places` 행을 만들고 `place_submissions.place_id`를 같은 트랜잭션에서 연결합니다. 반려는 사유 · 심사자 · 처리 시각만 남기고 장소는 만들지 않습니다. 이미 처리한 제보의 재심사는 409입니다. 관리자 API는 비로그인 401, 회원 403입니다. 관리자 로그인과 `admin_action_logs` 기록은 만들지 않았고, 심사자 id는 제보 행의 `reviewer_admin_id`에 남습니다. 승인된 장소에는 `visibility` · `owner_id`를 두지 않습니다. 이 두 컬럼의 일반 규칙은 8.3에 그대로 열려 있습니다.

### route
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| GET | `/travel-times?originPlaceId&destinationPlaceId&mode` | `TravelTimeQueryUseCase.between` |

### savedplace
저장 · 수정 · 삭제는 **여러 건을 한 번에** 처리합니다. 프론트(`entities/place/api/placeApi.ts`)와 mock(`mocks/handlers/places.ts`)이 팀에서 정한 이 형식으로 만들어져 있어, 이전의 한 건 단위 경로(`/saved-places/{savedPlaceId}`, `/visit`, `/batch`)를 대신합니다 (2026-11-26).

| 메서드 | 경로 | 입력 포트 · 메서드 | 상태 |
| --- | --- | --- | --- |
| GET | `/saved-places` | `SavedPlaceQueryUseCase.myList` | 구현됨 |
| GET | `/saved-places?bbox=` | `SavedPlaceQueryUseCase.myInBounds` | 이후 설계. 프론트는 전체 목록을 받아 화면에서 거름 |
| POST | `/saved-places` | `SavedPlaceCommandUseCase.save` | 구현 대상 (#145). 게시물 찜 · 링크 추출 결과 저장 모두 이 API |
| PATCH | `/saved-places` | `SavedPlaceCommandUseCase.update` | 구현 대상 (#145). 방문 상태 · 메모 |
| DELETE | `/saved-places` | `SavedPlaceCommandUseCase.delete` | 구현 대상 (#145) |
| GET · POST · DELETE | `/me/places/{placeId}/photos` | `SavedPlaceCommandUseCase.addPhotos / removePhotos` | 이후 구현. ERD `saved_place_photos` (8.1 #4) |
| GET · PUT | `/me/places/{placeId}/tags` | `SavedPlaceTagUseCase.tagsOf / replaceTags` | 이후 구현. ERD `saved_place_tags` (8.1 #7) |
| GET | `/users/{username}/saved-places` | `SavedPlaceQueryUseCase.publicMapOf` | 공개 기준 미정 (8.1 #3) |

**공통 규칙**
- 모두 로그인이 필요합니다. 비로그인은 401입니다.
- 장소는 공개 id(`places.public_id`), 저장 장소는 DB `id`를 문자열로 주고받습니다 (common 5.4).
- 본인의 저장 장소만 다룹니다. 요청한 `ids` 중 본인 것이 아닌 id와 없는 id는 **오류 없이 건너뜁니다** (mock과 같음).
- 응답의 저장 장소 한 건은 `GET /saved-places`의 항목(`SavedPlaceResponse`)과 같은 형식입니다.

**POST `/saved-places` — 저장 (찜콩)**

```json
// 요청
{ "placeIds": ["<장소 공개 id>", "..."], "collectionIds": [], "source": { "type": "POST", "refId": "...", "label": "○○님 게시물" } }
// 응답 200
{ "createdPlaceIds": ["새로 저장한 장소 공개 id"], "linkedPlaceIds": ["이미 저장되어 있던 장소 공개 id"] }
```

- `placeIds`의 중복은 하나로 합칩니다. 비어 있으면 400입니다.
- 장소가 없거나 볼 수 없으면(삭제됨, 남의 비공개 장소) 404이고, 이때는 아무것도 저장하지 않습니다.
- 이미 저장한 장소는 새로 만들지 않고 `linkedPlaceIds`에 넣습니다. 회원당 장소 하나는 한 번만 저장됩니다(`uk_saved_places_user_place`). 방문 상태 · 메모 · 저장 시각 · 처음 출처는 바뀌지 않습니다.
- 새로 저장한 장소는 `WANT_TO_GO`, 메모 없음으로 시작합니다.
- `source`를 생략하면 직접 저장(`SEARCH`)입니다. 출처는 ERD(`saved_places`)의 컬럼에 아래처럼 저장합니다. 출처는 처음 저장할 때만 남기고, 이미 저장한 장소를 다시 저장해도 바꾸지 않습니다.

| 요청 `source.type` | `source_type` | `source.refId` | `source.label` | 응답 `source.type` |
| --- | --- | --- | --- | --- |
| `POST` (게시물에서 찜) | `FEED` | 게시물 공개 id → `source_feed_id` | `source_label` | `POST` |
| `COLLECTION` (다른 사람 컬렉션에서 찜) | `COLLECTION` | 컬렉션 공개 id → `source_collection_id` | `source_label` | `COLLECTION` |
| `LINK_IMPORT` (AI 링크 가져오기) | `LINK_IMPORT` | 원문 URL → `source_url` | `source_label` | `LINK_IMPORT` |
| `SEARCH` (검색 · 지도에서 저장) | `SEARCH` | 없음 | `source_label` | `SEARCH` |
| `CUSTOM` (직접 등록한 장소) | `CUSTOM` | 없음 (대상은 `place_id`로 알 수 있음) | `source_label` | `CUSTOM` |
| (API로 받지 않음) | `PLAN_VISIT` | 일정 방문 기록으로 저장할 때 서버가 씀 | | `SEARCH`로 보냄 (프론트에 값 없음) |

- 출처 값은 ERD 메모(`saved_places.source_type`)를 따릅니다. 프론트가 게시물을 `POST`라고 부르는 것만 `FEED`로 바꾸고 나머지는 이름이 같습니다.
- `source_feed_id`는 `FEED`일 때만, `source_collection_id`는 `COLLECTION`일 때만 채웁니다(둘 중 최대 1개, ERD 제약 ④). 게시물 · 컬렉션 공개 id를 내부 id로 바꾸는 조회는 savedplace의 `adapter/out/persistence/query/` 아래 `*QueryAdapter`에서 `feeds` · `collections`를 읽기만 합니다(6.1의 조회 예외). 공개 id에 해당하는 게시물 · 컬렉션이 없으면 출처 id 없이 종류 · 문구만 저장합니다.
- 응답의 `source.refId`는 게시물 · 컬렉션의 공개 id, 링크 가져오기는 원문 URL입니다. `source.label`은 저장한 `source_label`이고, 없으면 종류별 기본 문구("게시물에서 저장" 등)입니다.
- **링크 가져오기의 `refId`:** 지금 프론트 · mock은 추출 작업 id를 보내지만, ERD에는 작업 id를 담을 칸이 없고 `source_url`(원문 주소)만 있습니다. 프론트가 원문 URL을 보내도록 바꿉니다(프론트 작업).
- **`source_collection_id`:** `collections` · `collection_places` 테이블은 V19에서 만들었습니다. `source_collection_id` 컬럼과 FK는 savedplace의 저장 API 작업에서 추가합니다. 그 전에는 `COLLECTION` 출처를 종류 · 문구만 저장합니다.
- `collectionIds`(저장하면서 컬렉션에 담기)는 이벤트로 collection에 넘깁니다(8.1 #13, `SavedPlaceCollectionsChangedEvent`). 테이블은 V19로 생겼지만, collection의 리스너를 만들기 전까지는 받기만 하고 저장하지 않습니다.

**DB 맞추기 (구현 예정, 마이그레이션 미작성):** 지금 `V17__create_saved_places.sql`은 이전 JPA 엔티티를 기준으로 만들어 ERD와 다릅니다. 저장 · 수정 · 삭제(#145)를 구현할 때 새 마이그레이션으로 ERD에 맞춥니다.

| 항목 | 지금 (V17) | ERD 기준으로 바꿀 것 |
| --- | --- | --- |
| 출처 종류 | `source` VARCHAR(30), NULL 허용, CHECK 없음 | `source_type` VARCHAR(15) NOT NULL, CHECK `FEED` · `COLLECTION` · `LINK_IMPORT` · `SEARCH` · `CUSTOM` · `PLAN_VISIT` |
| 출처 문구 · 원본 URL | 없음 | `source_label` VARCHAR(100), `source_url` TEXT 추가 |
| 출처 컬렉션 | 없음 | `source_collection_id` (FK → collections). `collections`는 V19로 생겼으므로 저장 API 작업에서 추가 |
| 출처 id 제약 | `source_feed_id`는 FEED일 때만 | + `source_collection_id`는 COLLECTION일 때만, 둘 중 최대 1개 |
| 방문 상태 | CHECK에 `NOT_INTERESTED` 포함 | `WANT_TO_GO` · `VISITED`만 |
| 메모 | TEXT | VARCHAR(500) |

코드의 `SavedPlaceSource`(`FEED` · `COLLECTION_SNAPSHOT` · `AI_IMPORT` · `DIRECT`)와 `VisitStatus`(`NOT_INTERESTED` 포함)도 같은 작업에서 ERD 값으로 바꿉니다.

**PATCH `/saved-places` — 방문 상태 · 메모 수정**

```json
// 요청: ids 외의 값은 모두 선택. 보낸 값만 바꿉니다
{ "ids": ["12", "15"], "visitStatus": "VISITED", "memo": "다음엔 창가 자리" }
// 응답 200: 바뀐 저장 장소 목록 (본인 것만)
[ { "id": "12", "place": { ... }, "visitStatus": "VISITED", ... } ]
```

- `visitStatus`는 `WANT_TO_GO` · `VISITED`만 받습니다(ERD 제약 ④). 다른 값은 400입니다.
- `VISITED`로 바꾸면 `visited_at`에 현재 시각을, `WANT_TO_GO`로 바꾸면 `null`을 넣습니다.
- `memo`는 500자까지입니다(ERD). 빈 문자열은 `null`(메모 없음)로 저장합니다. 태그는 별도 API(`/me/places/{placeId}/tags`, 8.1 #7)입니다.
- 컬렉션 필드(`addCollectionIds` · `setCollectionIds` · `removeCollectionIds`)도 같은 이벤트로 collection에 넘깁니다(8.1 #13). 컬렉션 마이그레이션 전에는 무시합니다.

**DELETE `/saved-places` — 삭제**

```json
// 요청 본문
{ "ids": ["12", "15"] }
// 응답 204
```

- 저장 기록만 지웁니다. 공용 장소(`places`)와 일정의 장소는 지우지 않습니다.
- 지우기 직전에 같은 트랜잭션에서 `SavedPlaceDeletingEvent`를 발행해 컬렉션 구성 장소를 먼저 정리합니다(6.3). 컬렉션 테이블이 생기기 전까지는 듣는 쪽이 없습니다.
- 본문이 있는 DELETE를 쓰는 이유는 여러 개를 고른 삭제를 한 번에 처리하기 위해서입니다. Spring은 `@RequestBody`로 받습니다.

### feed
| 메서드 | 경로 | 입력 포트 · 메서드 | 비고 |
| --- | --- | --- | --- |
| GET | `/feeds?tab=following\|recommended` | `FeedQueryUseCase.feed` | 추천은 비로그인 허용 |
| GET | `/feeds/places?tab=` | `FeedQueryUseCase.feedPlaces` | |
| POST | `/feeds` | `FeedCommandUseCase.write` | |
| GET · PUT · DELETE | `/feeds/{feedId}` | `FeedQueryUseCase.getDetail` / `FeedCommandUseCase.update / delete` | feedId = `feeds.public_id`. 삭제는 소프트 삭제(`deleted_at`, 204)이고 댓글 · 반응 · 사진 · 장소 연결은 남김(조회는 `deleted_at`으로 거름). 삭제된 게시물의 수정 · 재삭제는 404 (#177) |
| GET | `/feeds/{feedId}/comments` | `CommentQueryUseCase.list` | 댓글 + 대댓글 일부 |
| GET | `/feeds/{feedId}/comments/{commentId}/replies` | `CommentQueryUseCase.replies` | |
| POST · PUT · DELETE | `/feeds/{feedId}/comments/**` | `CommentCommandUseCase.write / edit / delete` | |
| POST · DELETE | `/feeds/{feedId}/likes` | `FeedReactionUseCase.likeFeed / unlikeFeed` | |
| POST · DELETE | `/feeds/{feedId}/comments/{commentId}/likes` | `FeedReactionUseCase.likeComment / unlikeComment` | |
| POST · DELETE | `/feeds/{feedId}/bookmarks` | `FeedReactionUseCase.bookmark / unbookmark` | |
| GET | `/me/bookmarked-feeds` | `FeedQueryUseCase.myBookmarks` | |
| GET | `/users/{username}/feeds` | `FeedQueryUseCase.feedsOf` | |
| GET | `/users/{username}/visits` | `FeedQueryUseCase.visitedPlacesOf` | 기준 미정 (8.3 #3) |

### collection
| 메서드 | 경로 | 입력 포트 · 메서드 | 비고 |
| --- | --- | --- | --- |
| GET · POST | `/collections` | `CollectionQueryUseCase.myCollections` / `CollectionCommandUseCase.create` | |
| GET | `/collections/discover` | `CollectionQueryUseCase.discover` | `is_listed`, PUBLIC |
| GET · PUT · DELETE | `/collections/{collectionId}` | `CollectionQueryUseCase.getDetail` / `CollectionCommandUseCase.update / delete` | 권한별 노출 · 잠금 |
| POST | `/collections/{collectionId}/items` | `CollectionCommandUseCase.addItem` | |
| PUT · DELETE | `/collections/{collectionId}/items/{itemId}` | `CollectionCommandUseCase.updateItem / removeItem` | 수정할 컬럼 없음 (8.1 #6) |
| PUT | `/collections/{collectionId}/items/reorder` | `CollectionCommandUseCase.reorderItems` | |
| GET | `/collections/{collectionId}/versions`, `/{versionNo}` | `CollectionQueryUseCase.versions / version` | 소유자 전용(제안) |
| GET | `/users/{username}/collections` | `CollectionQueryUseCase.collectionsOf` | 구현됨. 프론트 `CollectionDto` 배열, 최신순. 본인은 모든 공개 범위, 다른 회원은 `Visibility.allows`가 허용하는 것만(`follows` 마이그레이션 전까지 PUBLIC만). 삭제 · 관리자 숨김은 본인에게도 숨김. 표지가 없으면 보는 사람이 볼 수 있는 첫 장소 사진. 소유자는 프로필과 같은 기준(ACTIVE만, 그 외 404 `USER_NOT_FOUND`), 로그인 필요(이유는 `/users/{username}`과 같음). `isPurchased`는 구매 테이블 전까지 false, `isLocked` = 유료 · 남의 것 · 미구매 |
| GET | `/me/purchases`, `/me/purchases/{purchaseId}` | `PurchaseQueryUseCase.myPurchases / getPurchase` | |
| GET | `/me/purchases/{purchaseId}/snapshot` | `PurchaseQueryUseCase.openSnapshot` | 첫 열람 기록 (5.2.5) |
| POST | `/purchases/{purchaseId}/refunds` | `PurchaseRefundUseCase.requestRefund` | 판정 후 payment 호출 |

### plan
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| GET · POST | `/plans` | `PlanQueryUseCase.myPlans` / `PlanCommandUseCase.create` |
| GET · PUT · DELETE | `/plans/{planId}` | `PlanQueryUseCase.getDetail` / `PlanCommandUseCase.update / delete` |
| POST | `/plans/{planId}/items` | `PlanCommandUseCase.addItem` |
| PUT · DELETE | `/plans/{planId}/items/{itemId}` | `PlanCommandUseCase.updateItem / removeItem` |
| PUT | `/plans/{planId}/items/reorder` | `PlanCommandUseCase.reorderItems` |
| PUT | `/plans/{planId}/items/{itemId}/reservation` | `PlanCommandUseCase.putReservation` |
| PATCH | `/plans/{planId}/items/{itemId}/visit` | `PlanCommandUseCase.changeVisit` |
| POST | `/plans/{planId}/route-preview` | `PlanRouteUseCase.preview` |
| POST | `/plans/{planId}/route-optimizations` | `PlanRouteUseCase.optimize` |
| GET | `/plans/{planId}/weather` | `PlanQueryUseCase.weather` |
| GET | `/users/{username}/plans` | `PlanQueryUseCase.publicPlansOf` (공개 범위 컬럼 없음, 8.1 #1) |

### placeimport
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| POST | `/place-imports` | `PlaceImportUseCase.request` |
| GET | `/place-imports/{jobId}` | `PlaceImportUseCase.getStatus` |
| DELETE | `/place-imports/{jobId}` | `PlaceImportUseCase.cancel` |

### conversation
| 메서드 | 경로 · 목적지 | 입력 포트 · 메서드 |
| --- | --- | --- |
| GET | `/conversations` | `ConversationQueryUseCase.myConversations` |
| POST | `/conversations/direct` | `ConversationCommandUseCase.openDirect` (있으면 기존 반환) |
| POST | `/conversations/group` | `ConversationCommandUseCase.createGroup` |
| GET | `/conversations/{conversationId}` | `ConversationQueryUseCase.getDetail` |
| GET | `/conversations/{conversationId}/messages` | `ConversationQueryUseCase.messages` |
| PATCH | `/conversations/{conversationId}/read` | `MessageUseCase.markRead` (HTTP 대체 수단) |
| DELETE | `/conversations/{conversationId}/members/me` | `ConversationCommandUseCase.leave` |
| STOMP SEND | `/app/dm.send` | `MessageUseCase.send` |
| STOMP SEND | `/app/dm.read` | `MessageUseCase.markRead` |
| STOMP SUBSCRIBE | `/user/queue/dm`, `/user/queue/dm-read`, `/user/queue/dm/errors` | 출력: `MessagePushPort` · `@MessageExceptionHandler` |

### notification
| 메서드 | 경로 | 입력 포트 · 메서드 |
| --- | --- | --- |
| GET | `/notifications` | `NotificationUseCase.list` |
| GET | `/notifications/unread-count` | `NotificationUseCase.unreadCount` |
| PATCH | `/notifications/{notificationId}/read` | `NotificationUseCase.markRead` |

### moderation · creator · settlement · payment (관리자 API 포함)
| 메서드 | 경로 | 모듈 | 입력 포트 · 메서드 |
| --- | --- | --- | --- |
| POST | `/reports` | moderation | `ReportUseCase.report` (로그인 회원 접수. 게시물·댓글, 201) |
| GET | `/admin/reports` | moderation | `ReportAdminUseCase.list` |
| POST | `/admin/reports/{reportId}/resolve` | moderation | `ReportAdminUseCase.resolve` |
| GET | `/admin/users` | moderation | `UserAdminQueryUseCase.list` (`q`, `filter`, 배열 응답) |
| POST | `/admin/users/{userId}/sanctions` | moderation | `SanctionUseCase.sanction` → `UserStatusUseCase`, 201 |
| DELETE | `/admin/users/{userId}/sanctions/{sanctionId}` | moderation | `SanctionUseCase.revoke`, 204 |
| GET | `/admin/feeds` | feed | `FeedAdminQueryUseCase.list` (`q`, 배열 응답) |
| PATCH | `/admin/feeds/{feedId}` | feed | `FeedModerationUseCase.setHidden`, UUID, 200 |
| POST | `/creators/apply` | creator | `CreatorUseCase.apply` |
| GET | `/me/creator` | creator | `CreatorUseCase.myStatus` |
| GET · PUT | `/me/creator/payout-account` | creator | `PayoutAccountUseCase.get / register` |
| GET | `/admin/creators/apply` | creator | `CreatorReviewUseCase.pendingApplications` |
| PATCH | `/admin/creators/apply/{userId}` | creator | `CreatorReviewUseCase.review` (승인 · 거절) |
| PATCH | `/admin/creator/deprive/{userId}` | creator | `CreatorReviewUseCase.suspend` |
| GET | `/me/creator/settlements`, `/{settlementId}` | settlement | `SettlementQueryUseCase.mySettlements / getDetail` |
| POST | `/admin/settlements/{settlementId}/confirm` | settlement | `SettlementAdminUseCase.confirm` |
| POST | `/payments/prepare` | payment | `PaymentUseCase.prepare` |
| POST | `/payments/confirm` | payment | `PaymentUseCase.confirm` |
| GET | `/me/payments` | payment | `PaymentQueryUseCase.myPayments` |

관리자 로그인 API와 세션 발급은 아직 별도 범위입니다 (8.1 #8). 현재 관리자 API는 `auth/adapter/in/security/SecurityConfig`의 공통 체인에서 `ROLE_ADMIN`을 검사합니다. `common/security/AdminAccountStatusFilter`는 `AdminAccessVerifier` SPI를 통해 `admin/adapter/out/persistence/AdminAccessVerifierAdapter`가 확인한 ACTIVE 관리자만 허용합니다. 회원 JWT는 `ROLE_USER`만 부여하며 관리자 계정으로 전환하지 않습니다. 실제 관리자 ID는 `@CurrentAdmin AdminPrincipal`에서 읽고, 테스트에서만 인증 주체를 주입합니다.

#### 사용자 제재·콘텐츠 관리 구현 현황 (2026-11-26)

아래 내용과 공통 청사진 5.8은 실제 코드와 이번 작업의 구현 기준을 기록합니다. 팀 전체 정책 합의나 외부 API 명세 공유가 완료되었다는 뜻은 아닙니다.

- `moderation`은 제재 이력을, `user`는 계정 상태와 정지 만료일을 관리합니다. 상태 변경은 공개 Input Port로 요청하며, `feed`의 게시물·댓글 숨김도 공개 `FeedModerationUseCase`로 처리합니다. `admin`은 작업 로그를 담당합니다.
- 프론트 유형 `WARNING · SUSPEND_7 · SUSPEND_30 · BAN`을 명령 경계에서 도메인 `WARNING · SUSPEND · BAN`과 168/720시간으로 변환합니다. WARNING은 계정을 바꾸지 않고, 기간 정지는 SUSPENDED와 만료일, 영구 정지는 BANNED와 NULL 만료일을 저장합니다. 탈퇴 진행·완료 계정에는 신규 제재를 허용하지 않습니다.
- 반복 경고는 별도 이력으로 보존합니다. 유효한 기간 정지 중 추가 기간 정지와 유효한 BAN 중 추가 이용 제한은 409입니다. BAN 전환은 기존 기간 정지를 `SUPERSEDED_BY_BAN`으로 종료하고 원래 만료일·사유를 보존합니다. 특정 제재 해제는 `REVOKED`, 자동 만료는 `EXPIRED`로 구분합니다. 다른 유효한 이용 제한이 있으면 ACTIVE로 복구하지 않습니다.
- 로그인·Access Token 인증·토큰 발급 및 재발급에서 현재 계정을 확인합니다. 만료된 기간 정지는 `UserSuspensionExpiryCheckEvent`의 동기 리스너가 `SanctionUseCase.expireDue`로 확인합니다. `user → moderation` 직접 의존이나 별도 만료 스케줄러는 추가하지 않았습니다. 토큰 쓰기는 사용자 행을 먼저 잠그며, 제재 시 모든 Refresh Token 폐기는 `UserStatusChangedEvent`의 AFTER_COMMIT 리스너가 별도 트랜잭션에서 수행합니다.
- 게시물은 삭제되지 않은 모든 공개 범위와 숨김 상태를 관리자 목록에 포함합니다. 숨김·복구는 기존 `visibility`를 바꾸지 않고 `hidden_at`만 변경합니다. 작성자·관리자는 숨김 콘텐츠를 조회할 수 있고, 다른 사용자와 비회원에게는 404입니다. 숨긴 게시물의 신규 댓글·좋아요·북마크와 숨긴 댓글의 신규 답글·좋아요를 막고 기존 데이터는 보존합니다.
- 업무 변경·신고 처리 결과·관리자 작업 로그는 같은 트랜잭션입니다. 직접 처리로 신고를 자동 종료하지 않으며, 신고 처리에서는 선택한 신고만 종료합니다. 멱등 숨김·복구에는 작업 로그를 중복 기록하지 않습니다. 제재·숨김 사유는 필수, 해제·복구 사유는 선택이며 각각 최대 200 Unicode 코드 포인트입니다.
- V14는 `admins`, `admin_action_logs`, V15는 `user_sanctions`, V16은 users의 BANNED CHECK 확장을 담당합니다. 원인 신고는 신규 테이블의 nullable 참조로 보존하고, 제재의 원인 신고 UNIQUE는 해제 후에도 유지합니다. 미종료 이용 제한 UNIQUE는 `ended_at IS NULL`이며 현재 시각을 인덱스 조건으로 사용하지 않습니다. 기존 V1~V13과 신고 스냅샷·V13 부분 UNIQUE는 변경하지 않았습니다.
- 컬렉션, 관리자 로그인 전체, 경고 알림, 사용자 상세·제재 이력 전용 화면, 페이지네이션과 기간 연장은 후속 범위입니다.

신고 목록은 `?status=PENDING|RESOLVED`이고 응답은 배열입니다. 공통 목록 규칙의 `page · size`는 이 화면에 페이지가 생기기 전까지 쓰지 않습니다. 정렬은 접수 시각 내림차순, 같으면 id 내림차순입니다. 저장 상태와 처리 액션은 분리합니다. 상태는 `PENDING · RESOLVED`, 액션은 `DISMISS · HIDE_CONTENT · SANCTION_USER`입니다. 도메인의 게시물 대상은 `FEED`이고 관리자 응답에서만 `POST`로 바꿉니다. 기각·사용자 제재·게시물 및 댓글 숨김을 저장합니다. 기각은 선택한 신고만 `SELECT … FOR UPDATE`로 잠그고, 그 행이 아직 `PENDING`일 때만 `RESOLVED`로 바꿉니다. 같은 대상의 다른 대기 신고, 이미 완료된 행, 처리 중에 새로 들어온 신고는 바꾸지 않습니다. 같은 신고를 동시에 기각하면 한쪽만 성공하고 다른 쪽은 409 `ALREADY_RESOLVED`입니다. 사용자 제재는 `SanctionUseCase`, 게시물·댓글 숨김은 `FeedModerationUseCase`를 재사용합니다. 컬렉션 숨김·제재만 501 `REPORT_ACTION_NOT_SUPPORTED`로 대기에 남습니다. mock도 어떤 액션이든 선택한 신고만 닫습니다. 대상 미리보기는 신고 id마다 따로 만듭니다. 같은 대상이라도 신고마다 저장된 `snapshot`을 유지하고, 현재 글을 읽을 때는 대상 종류별로 한 번씩 조회합니다. `target.text`는 스냅샷 첫 줄이고, 스냅샷이 없으면 현재 첫 줄, 그것도 없으면 상태별 안내입니다. `target.imageUrl`은 스냅샷에 저장된 이미지만 쓰고 현재 내용으로 채우지 않습니다. `target.link`는 `visibility`가 `LIVE`일 때만 둡니다. `target.snapshot`은 저장된 원문 `{text, imageUrl}`이고 비어 있으면 null입니다. 현재 글을 복사해 채우지 않습니다. 컬럼은 기존 `snapshot TEXT`입니다. 회원 접수가 저장하는 전체 본문 스냅샷은 `{text, imageUrl: null, reportedUserId, textScope:"FULL"}`입니다. `text`가 null이면 본문 누락이고 빈 문자열은 빈 본문입니다. 이미지는 넣지 않고 비교하지 않습니다. 저장한 스냅샷은 글이 바뀌거나 신고를 기각해도 고치지 않습니다. 응답 `reportedUser.id`는 게시물·댓글 행이 있으면 그 `user_id`이고 숨김·소프트 삭제도 같습니다. 행이 없으면 스냅샷의 `reportedUserId`를 쓰며, 대상 id를 작성자 id로 쓰지 않습니다. `textScope`는 관리자 응답과 접수 응답 어디에도 넣지 않습니다. `target.current`는 `LIVE`·`HIDDEN`일 때만 `{text, imageUrl: null}`이고, `DELETED`·`UNAVAILABLE`이면 null입니다. 게시물·댓글의 `current.text`는 현재 본문 전체이고, 회원은 `닉네임 (@username)`입니다. 값은 비교용으로 자른 문자열이 아니라 조회한 원문입니다. `target.modified`는 `true`·`false`·`null`입니다. 게시물·댓글에서 `textScope`가 정확히 `"FULL"`이면 본문 전체를 비교합니다. 평문 스냅샷, `textScope`가 없는 스냅샷, null, `"FULL"`이 아닌 값은 첫 줄만 비교합니다. 회원은 표시 이름의 첫 줄만 비교합니다. 컬렉션과 이미지는 비교하지 않아 null입니다. 스냅샷이 없거나 본문이 누락됐거나 현재 본문을 읽을 수 없거나 스냅샷을 해석할 수 없으면 null입니다. 양쪽이 빈 문자열이면 false이고, 한쪽만 빈 문자열이면 true입니다. 비교 전에 CRLF와 단독 CR을 LF로 맞추고 본문 양끝 공백만 제거합니다. 중간 공백, 빈 줄, 대소문자, HTML, Markdown은 유지합니다. `false`는 그 범위의 글이 지금과 같다는 뜻입니다. `target.visibility`는 `LIVE · HIDDEN · DELETED · UNAVAILABLE`입니다. `deleted_at`이 있으면 `DELETED`이고 현재 글은 비웁니다. `hidden_at`만 있으면 `HIDDEN`이며 현재 글은 보여 주고 링크는 없습니다. 행이 없거나 컬렉션이면 `UNAVAILABLE`이고 "삭제됨"으로 쓰지 않습니다. 알려진 삭제 안내는 게시물·댓글 "삭제된 …", 사용자 "탈퇴한 사용자"입니다. `handled_by`에는 과거 데이터 검증 없는 FK를 추가하지 않았습니다. 신고 결과·제재·숨김·관리자 작업 로그는 같은 트랜잭션으로 저장하고, 알림 발송은 구현하지 않았습니다.

회원 접수 `POST /reports`는 `ReportService`가 처리합니다. 로그인 회원(`LoginUser`)만 호출하고, 신고자 id는 요청 본문이 아니라 인증 주체에서 읽습니다. 본문에 신고자 · 작성자 · 본문 · 이미지를 넣어도 저장하지 않습니다. 로그인하지 않으면 401이고, POST의 CSRF와 필수 약관 재동의는 기존 필터를 그대로 탑니다. 컨트롤러 경로는 `/reports`이고 서블릿 `/api/v1`과 겹치지 않아 공개 주소는 `/api/v1/reports`입니다. 요청은 `{targetType, targetId, reason, detail}`입니다. `targetType`은 `POST`와 `FEED`를 게시물로 받고 `COMMENT`만 댓글입니다. 응답 201은 `{id, targetType, targetId, reason, status, createdAt}`이고 게시물은 `POST`, 상태는 `PENDING`입니다. 스냅샷 · 상세 · 작성자는 응답에 없습니다. 접수 사유는 `SPAM · ABUSE · IMPERSONATION · COPYRIGHT · INAPPROPRIATE · ETC`만 받습니다. `reports.reason` 컬럼은 문자열이라 이 목록으로 바꾸지 않았고, 관리자 목록은 기존 다른 문자열도 그대로 보여 줍니다. 상세는 선택이고 `ETC`만 필수입니다. 공백만 있으면 빈 값이며 500자(코드 포인트)를 넘으면 400 `REPORT_DETAIL_TOO_LONG`입니다. 관리자 처리 메모 200자와는 별개입니다. 대상은 있고, 삭제 · 숨김이 아니고, 현재 회원이 볼 수 있어야 합니다. 다른 회원 글은 `PUBLIC`만 신고할 수 있습니다. 작성자 본인 글은 공개 범위와 관계없이 조회한 뒤 400 `SELF_REPORT`로 거절합니다. 없거나 볼 수 없으면 둘 다 404 `REPORT_TARGET_NOT_FOUND`이고 작성자 · 본문을 알려 주지 않습니다. 댓글은 부모 게시물도 같은 조건으로 볼 수 있어야 하고, 자기 신고 여부는 댓글 작성자 id로 판단합니다. 상위 게시물 작성자는 타인의 댓글을 신고할 수 있습니다. 알 수 없는 공개 범위는 아무도 신고할 수 없습니다. `FOLLOWERS`도 다른 회원에게는 열지 않습니다. 피드 목록은 PUBLIC만 반환하고 `follows` 마이그레이션이 없으므로, 피드 상세 조회도 공개 범위와 관리자 숨김을 검사하며 신고 제한을 유지합니다. 팔로우 · 차단 테이블은 조회하지 않습니다. 회원 · 컬렉션은 400 `INVALID_REPORT_TARGET`입니다. 같은 신고자 · 대상 · `PENDING`이 있으면 사유가 달라도 409 `DUPLICATE_REPORT`입니다. 앱의 `existsPending`과 부분 유니크 인덱스 `ux_reports_pending_reporter_target`(V13, `reporter_id, target_type, target_id`, `status = 'PENDING'`)가 같이 막고, 그 인덱스 위반(`SQLState 23505`)만 중복으로 바꿉니다. V7과 약관 V12는 수정하지 않습니다. 같은 신고자·대상의 `PENDING`이 이미 둘 이상이면 V13 적용은 실패하고, 기존 행·스냅샷·처리 이력은 지우거나 바꾸지 않습니다. 그 데이터가 확인되면 배포를 보류합니다. `RESOLVED` 뒤에는 같은 대상을 새 행으로 다시 신고할 수 있고, 이전 행과 스냅샷은 다시 열지 않습니다. 대상 조회는 `ReportTargetLookupPort`를 JDBC로 구현하고 feed · social 엔티티를 주입하지 않습니다. 스냅샷은 `ReportSnapshot.full`로 만든 뒤 `ReportSnapshotWriter`가 저장 트랜잭션 안에서 JSON으로 씁니다. 관리자 미리보기는 그 `snapshot` 컬럼을 신고 id마다 읽고, `textScope`가 정확히 `"FULL"`인 게시물 · 댓글만 전체 본문을 비교합니다. 관리자 숨김·제재 API는 구현되어 있으며 회원 신고 화면은 아직 없습니다.

---

## 4. 전체 패키지 트리와 모듈 내부 규칙

### 4.1 모듈 내부 표준 구조와 만드는 조건

```
<module>/
├── domain/
│   ├── model/          애그리거트 · 엔티티
│   ├── vo/             값 객체 (검증이 있는 값)
│   ├── policy/         여러 모델에 걸친 규칙, 계산
│   ├── event/          다른 모듈이 구독하는 도메인 이벤트
│   └── <Module>ErrorCode.java
├── application/
│   ├── port/
│   │   ├── in/         UseCase 인터페이스 (모듈의 입구)
│   │   └── out/        PersistencePort · QueryPort · 외부 시스템 Port
│   ├── service/        UseCase 구현, 트랜잭션 경계
│   └── dto/            Command · Query · Result (record)
└── adapter/
    ├── in/
    │   ├── web/        Controller + request/ + response/ (+ admin/)
    │   ├── websocket/  STOMP 핸들러 (conversation만)
    │   ├── event/      다른 모듈 이벤트 리스너          ← 추가
    │   └── scheduler/  @Scheduled 작업                 ← 추가
    └── out/
        ├── persistence/  Adapter + entity/ + repository/ + mapper/
        └── external/     <대상>/ 외부 API 어댑터
```

| 패키지 | 만드는 조건 | 만들지 않는 경우 |
| --- | --- | --- |
| `domain/model` | 상태 전이 · 불변식이 있는 애그리거트가 있을 때 (4.2 1단계) | 관계·로그 테이블만 있는 모듈 (`social`, `notification`) |
| `domain/vo` | 같은 검증이 두 곳 이상에서 필요하거나, 원시 타입으로는 의미가 드러나지 않을 때 (`CollectionPrice`, `FeeRate`, `Username`) | 검증 없는 단순 값 |
| `domain/policy` | 규칙이 **여러 모델에 걸치거나, 다른 모듈의 데이터를 입력값으로 받아야 할 때** (`CollectionAccessPolicy`, `VisitOrderOptimizer`) | 모델 하나의 메서드로 충분한 규칙 |
| `domain/event` | 다른 모듈(또는 커밋 후 비동기 작업)이 반응해야 할 때 | 같은 서비스 안에서 이어서 처리하면 되는 일 |
| `application/dto` | in-port가 있으면 항상 | |
| `port/out` 외부 Port | 외부 시스템을 호출할 때 | 다른 **모듈**을 호출할 때 (그 모듈의 in-port를 바로 씀, 6.1) |
| `adapter/in/web/admin` | 그 모듈에 관리자 API가 있을 때 | |
| `adapter/out/persistence/mapper` | 자식이 있는 애그리거트처럼 변환이 10줄을 넘을 때 | 단순 변환은 JPA 엔티티의 `from(domain)` / `toDomain()` 메서드로 |
| `adapter/out/external/<대상>` | 외부 API 하나마다 | |

### 4.2 domain 모델과 JPA 엔티티 분리 기준

| 단계 | 기준 | 구조 | 해당 테이블 |
| --- | --- | --- | --- |
| **1. 분리** | 상태 전이 · 금액 계산 · 권한 규칙이 있음 | `domain/model` + `persistence/entity` + 변환 | users, feeds, comments, places, saved_places, collections(+places·versions), collection_purchases, plans(+items·reservations), payments, refunds, creator_settlements, creators, creator_payout_accounts, import_jobs, conversations(+members), messages, media_files, reports, user_sanctions, refresh_tokens, terms |
| **2. 모델 없음** | 규칙이 "있다/없다", "추가만" 수준 | domain 모델 없이 **포트 메서드가 원시값·dto를 받음** (`follow(followerId, followeeId)`). JPA 엔티티는 adapter 안에만 | follows, user_blocks, feed_likes, comment_likes, feed_bookmarks, notifications, user_settings, user_map_category_settings, user_terms_agreements, user_social_accounts, creator_status_histories, place_external_refs, import_extraction_runs, admin_action_logs |
| **3. 금지** | — | JPA 엔티티를 application이나 domain에서 쓰는 것 | 전부 |

- 2단계 테이블에도 규칙이 생기면(예: 알림 묶음 처리) 그때 1단계로 올립니다.
- 2단계여도 **JPA 엔티티는 adapter 밖으로 나가지 않습니다.** 이것만 지키면 나중에 모델을 추가해도 application의 시그니처만 바뀝니다.

### 4.3 UseCase · Service · Port 개수 기준

| 대상 | 기준 | 예 |
| --- | --- | --- |
| in-port | **애그리거트(리소스)마다 Command 1개 + Query 1개**에서 시작. 흐름 중심 기능은 기능 이름 하나 | `PlanCommandUseCase`, `PlanQueryUseCase`, `LoginUseCase` |
| in-port 분리 | 메서드가 8개를 넘거나, 특정 메서드만 외부 시스템이 필요할 때 | `PlanRouteUseCase`(길찾기 필요)를 `PlanQueryUseCase`에서 분리 |
| 다른 모듈 전용 in-port | 누가 쓰는지 드러나도록 이름으로 구분 | `UserCounterUseCase`(social·feed), `PurchaseSettlementUseCase`(settlement) |
| service | in-port 하나당 구현 하나. 서비스끼리 서로 호출하지 않음 (공통 로직은 domain으로) | `PlanCommandService` |
| PersistencePort | **애그리거트마다 1개** (조회·저장·삭제를 한 인터페이스에) | `PlanPersistencePort` |
| QueryPort | **모듈마다 1개**, 화면 조회 전용. 커지면 화면 단위로 분리 | `PlanQueryPort` |
| 외부 Port | 외부 시스템(역할)마다 1개 | `WeatherPort`, `PaymentGatewayPort` |
| dto | Command · Query · Result는 필요한 것만. **Request와 Result가 같아도 Request/Response는 따로** (API 계약과 유스케이스가 따로 바뀜) | `CreatePlanCommand`, `PlanDetailResult` |

모듈 `terms`처럼 기능이 작으면 in-port 하나(`TermsUseCase`), 서비스 하나, PersistencePort 하나로 끝납니다.

### 4.4 전체 패키지 트리

> 각 모듈의 주요 클래스만 적었습니다. `plan`, `route`, `collection`, `payment`, `settlement`, `placeimport`, `conversation`은 5장에 상세 트리가 있습니다.
> `adapter/out/persistence`는 4.1 구조를 따르므로 특별한 것만 적습니다.

```
backend/src/main/java/com/pickong/
├── PickongApplication.java
│
├── common/                                   # 범용 기능만 (4.5)
│   ├── domain/                               # 공유 커널: Visibility, ViewerRelation, GeoPoint, BoundingBox
│   ├── error/                                # ErrorCode(interface), BusinessException, ErrorResponse, GlobalExceptionHandler
│   ├── web/                                  # CursorRequest, CursorPage<T>, PageResult<T>, @CurrentUser 리졸버
│   ├── security/                             # LoginUser, AdminPrincipal, AccessTokenVerifier(interface)
│   ├── persistence/                          # BaseTimeEntity, JpaConfig, PostGIS 변환기
│   ├── websocket/                            # WebSocketConfig (/ws, /app, /user, simple broker)
│   └── config/                               # AsyncConfig(작업별 Executor), SchedulingConfig, ClockConfig, OpenApiConfig
│
├── auth/
│   ├── domain/model/RefreshToken.java        # 만료 · 폐기 · 로테이션 · 재사용 감지 시 family 폐기
│   ├── application/
│   │   ├── port/in/                          # SignUpUseCase, LoginUseCase, TokenUseCase, PasswordResetUseCase
│   │   ├── port/out/                         # RefreshTokenPersistencePort, SocialAccountPort,
│   │   │                                     # TokenIssuerPort, PasswordHasherPort, PasswordResetTokenPort, MailSenderPort
│   │   ├── service/
│   │   └── dto/
│   └── adapter/
│       ├── in/web/AuthController.java
│       ├── in/security/                      # SecurityConfig(회원 체인), JwtCookieAuthenticationFilter,
│       │                                     # OAuth2LoginSuccessHandler, TokenCookieWriter
│       ├── in/websocket/                     # WebSocketAuthHandshakeInterceptor, StompDestinationGuard
│       ├── in/event/UserStatusChangedListener.java   # 탈퇴 · 영구 정지 → 토큰 폐기
│       ├── in/scheduler/ExpiredTokenCleanupScheduler.java
│       ├── out/persistence/
│       └── out/external/                     # jwt/JwtTokenAdapter, crypto/BCryptPasswordHasherAdapter, mail/SmtpMailAdapter
│
├── user/
│   ├── domain/
│   │   ├── model/User.java                   # ACTIVE · SUSPENDED · WITHDRAWN_PENDING · WITHDRAWN 전이, 익명화
│   │   ├── vo/Username.java                  # 영소문자·숫자·.·_ 3~20자
│   │   └── event/                            # UserWithdrawnEvent, UserStatusChangedEvent
│   ├── application/
│   │   ├── port/in/                          # UserCommandUseCase, UserQueryUseCase, UserSettingsUseCase,
│   │   │                                     # UserAccountUseCase(auth), UserStatusUseCase(moderation), UserCounterUseCase(social·feed)
│   │   ├── port/out/                         # UserPersistencePort, UserSettingsPersistencePort, UserQueryPort
│   │   ├── service/
│   │   └── dto/
│   └── adapter/
│       ├── in/web/                           # MeController, UserController, UsernameCheckController, SettingsController
│       ├── in/scheduler/WithdrawalAnonymizeScheduler.java
│       └── out/persistence/
│
├── social/                                   # 2단계: domain 모델 없음
│   ├── application/
│   │   ├── port/in/                          # RelationCommandUseCase(팔로우 · 차단), RelationQueryUseCase(목록 · 관계 판정)
│   │   ├── port/out/RelationPersistencePort.java
│   │   ├── service/
│   │   └── dto/                              # ViewerRelation 계산 결과 등
│   ├── domain/event/UserFollowedEvent.java
│   └── adapter/
│       ├── in/web/                           # FollowController, BlockController
│       └── out/persistence/
│
├── terms/
│   ├── domain/model/Terms.java               # 현재 버전 판정, 필수 여부
│   ├── application/                          # TermsUseCase, TermsPersistencePort
│   └── adapter/in/web/TermsController.java, adapter/out/persistence/
│
├── media/
│   ├── domain/model/MediaFile.java           # PENDING → 업로드 확인 → 처리 완료/실패, 재시도
│   ├── domain/event/MediaUploadedEvent.java
│   ├── application/
│   │   ├── port/in/                          # MediaUploadUseCase, MediaQueryUseCase(다른 모듈: 소유·상태 확인, URL), MediaProcessUseCase
│   │   └── port/out/                         # MediaPersistencePort, ObjectStoragePort, ImageProcessorPort
│   └── adapter/
│       ├── in/web/MediaController.java
│       ├── in/event/MediaUploadedListener.java       # 커밋 후 @Async 처리
│       ├── in/scheduler/                     # MediaRetryScheduler, OrphanMediaCleanupScheduler
│       └── out/external/                     # r2/R2StorageAdapter, image/ImageProcessorAdapter
│
├── place/
│   ├── domain/
│   │   ├── model/                            # Place, BusinessHours, HoursException, PlaceSubmission
│   │   ├── vo/                               # PlaceSourceType, PlaceSubmissionStatus. PlaceCategory · MinuteOfDay는 이후 설계
│   │   └── policy/OpeningHoursPolicy.java    # 특정 일시 영업 여부 (일정 자동 점검 보류 해제 시 사용)
│   ├── application/
│   │   ├── port/in/                          # PlaceQueryUseCase, PlaceSubmissionUseCase
│   │   │                                     # PlaceCommandUseCase, PlaceSearchUseCase는 이후 설계. 클래스 없음
│   │   ├── port/out/                         # PlacePersistencePort, PlaceSubmissionPersistencePort, SubmitterLookupPort
│   │   ├── service/                          # PlaceQueryService, PlaceSubmissionService
│   │   └── dto/                              # SubmitPlaceCommand, PlaceSubmissionView, SubmitterView
│   └── adapter/
│       ├── in/web/                           # PlaceController, PlaceSubmissionController
│       ├── in/web/admin/AdminPlaceSubmissionController.java
│       ├── out/persistence/                  # 좌표는 JDBC ST_MakePoint. Hibernate Spatial 의존성 없음.
│       │                                     # SubmitterLookupAdapter는 users의 id · public_id · username · display_name만 읽고 user 리포지토리는 쓰지 않음
│       └── out/external/kakao/               # KakaoLocalSearchAdapter는 이후 설계. 패키지 안내만 있음
│
├── route/                                    # → 5.1
├── savedplace/
│   ├── domain/
│   │   ├── model/SavedPlace.java             # 회원당 장소 1번, 방문 상태, 메모, 출처
│   │   ├── vo/                               # VisitStatus, SavedPlaceSource
│   │   └── event/                            # SavedPlaceDeletingEvent(삭제 직전), SavedPlaceCollectionsChangedEvent(저장 · 수정 직후). 같은 트랜잭션 (6.3)
│   ├── application/
│   │   ├── port/in/                          # SavedPlaceCommandUseCase, SavedPlaceQueryUseCase
│   │   └── port/out/                         # SavedPlacePersistencePort, SavedPlaceQueryPort
│   └── adapter/in/web/                       # SavedPlaceController, UserMapController
│
├── feed/
│   ├── domain/
│   │   ├── model/                            # Feed(+FeedPhoto, FeedPlace), Comment
│   │   ├── policy/FeedVisibilityPolicy.java  # 공개 범위 + 관계 + 삭제 여부 → 볼 수 있는가
│   │   └── event/                            # FeedLikedEvent, CommentCreatedEvent, CommentLikedEvent
│   ├── application/
│   │   ├── port/in/                          # FeedCommandUseCase, FeedQueryUseCase, CommentCommandUseCase,
│   │   │                                     # CommentQueryUseCase, FeedReactionUseCase
│   │   └── port/out/                         # FeedPersistencePort, CommentPersistencePort, ReactionPort(2단계), FeedQueryPort
│   └── adapter/in/web/                       # FeedController, CommentController, ReactionController, UserFeedController
│
├── collection/                               # → 5.2
├── plan/                                     # → 5.1
├── placeimport/                              # → 5.3
├── conversation/                             # → 5.4
│
├── notification/                             # 2단계: domain 모델 없음
│   ├── application/
│   │   ├── port/in/NotificationUseCase.java  # 목록 · 안 읽은 수 · 읽음 · 생성(리스너용)
│   │   └── port/out/NotificationPersistencePort.java
│   └── adapter/
│       ├── in/web/NotificationController.java
│       └── in/event/                         # SocialNotificationListener, FeedNotificationListener,
│                                             # CollectionNotificationListener, CreatorNotificationListener
│
├── moderation/                               # 회원 접수·신고 처리·사용자 조회·제재·해제 구현
│   ├── domain/
│   │   ├── ModerationErrorCode.java · ReportRules.java · ReportContentComparison.java
│   │   ├── model/                            # Report, ReportSnapshot(FULL · imageUrl null), Sanction
│   │   └── vo/                               # ReportTargetType, ReportStatus, ReportAction, ReportReason, SanctionType
│   ├── application/
│   │   ├── ReportSnapshots.java              # 조회 결과 → 전체 본문 스냅샷. 회원·컬렉션은 거절
│   │   ├── port/in/                          # ReportUseCase, ReportAdminUseCase, SanctionUseCase, UserAdminQueryUseCase
│   │   ├── port/out/                         # ReportPersistencePort(접수·조회·잠금·처리), SanctionPersistencePort, AdminUserQueryPort,
│   │   │                                     # ReportSnapshotWriter, ReportTargetLookupPort,
│   │   │                                     # ReportTargetPreviewPort, ReportPartyLookupPort
│   │   ├── service/                          # ReportService, ReportAdminService, SanctionService, UserAdminQueryService
│   │   └── dto/                              # ReportCommand, ReportReceipt, ReportTargetLookup, ReportView
│   └── adapter/
│       ├── in/web/ReportController.java      # POST /reports. request/CreateReportRequest, response/CreateReportResponse
│       ├── in/web/admin/AdminReportController.java · AdminUserController.java
│       └── out/
│           ├── persistence/                  # ReportPersistenceAdapter, ReportJpaEntity, ReportJpaRepository
│           │   └── query/                    # ReportTargetLookupAdapter · ReportTargetPreviewAdapter (JDBC)
│           └── snapshot/ReportSnapshotJsonAdapter.java   # text · imageUrl null · reportedUserId · textScope
│
├── creator/
│   ├── domain/
│   │   ├── model/                            # Creator(신청 · 승인 · 거절 · 박탈, 재신청), CreatorGrade, PayoutAccount
│   │   ├── vo/FeeRate.java                   # 0.30~0.35, 금액 × 수수료율 계산
│   │   └── event/                            # CreatorApprovedEvent, CreatorRejectedEvent, CreatorSuspendedEvent
│   ├── application/
│   │   ├── port/in/                          # CreatorUseCase, CreatorReviewUseCase(admin), PayoutAccountUseCase,
│   │   │                                     # SellerQueryUseCase(collection: 판매 가능 여부 · 수수료율)
│   │   └── port/out/                         # CreatorPersistencePort, PayoutAccountPersistencePort, EncryptionPort
│   └── adapter/
│       ├── in/web/CreatorController.java
│       ├── in/web/admin/AdminCreatorController.java
│       ├── in/scheduler/ResidentNumberPurgeScheduler.java
│       └── out/external/crypto/AesGcmEncryptionAdapter.java
│
├── settlement/                               # → 5.2
├── payment/                                  # → 5.2
│
└── admin/
    ├── domain/model/Admin.java               # SUPER_ADMIN · OPERATOR · CS, ACTIVE · DISABLED
    ├── application/
    │   ├── port/in/                          # AdminActionLogUseCase 구현. 관리자 로그인 포트·API는 후속 범위
    │   └── port/out/                         # AdminActionLogPersistencePort. 계정 로그인 저장 포트는 후속 범위
    └── adapter/
        ├── out/persistence/AdminAccessVerifierAdapter.java # common 보안 SPI 구현, ACTIVE 관리자 확인
        └── out/persistence/

backend/src/main/resources/
├── application.yml · application-local.yml · application-prod.yml
├── db/migration/                             # V1__init_schema.sql … (PostgreSQL 문법)
└── prompts/place-import/                     # v1.md … (파일 이름 = prompt_version)
```

### 4.5 `common`에 넣는 것과 넣지 않는 것

| 넣음 | 넣지 않음 |
| --- | --- |
| 오류 응답 형식, `BusinessException`, `ErrorCode` 인터페이스 | 모듈별 오류 코드 (각 모듈 `<Module>ErrorCode`) |
| 커서 페이지, `@CurrentUser`, 인증 주체 타입(`LoginUser`, `AdminPrincipal`) | 토큰 발급·검증 구현 (`auth`) |
| JPA 공통 설정, 생성·수정 시각, PostGIS 변환 | 특정 테이블 쿼리 |
| WebSocket 브로커 설정, Async Executor, Clock | 업무 스케줄 |
| **공유 커널** (아래 4개만) | 가격 규칙, 수수료 계산, 공개 범위 외의 권한 규칙, `StringUtils` 같은 잡동사니 |

**공유 커널(`common/domain`)** — 모든 모듈이 같은 의미로 쓰고, 바뀌면 어차피 모든 모듈을 고쳐야 하는 값만 둡니다.

| 타입 | 내용 | 둔 이유 |
| --- | --- | --- |
| `Visibility` | `PUBLIC` · `FOLLOWERS` · `PRIVATE` + `allows(ViewerRelation)` | 게시물 · 컬렉션 · 장소 · 설정이 같은 3단계 규칙을 씁니다 (프론트도 `shared/config/visibility.ts`) |
| `ViewerRelation` | `OWNER` · `FOLLOWER` · `OTHER` · `ANONYMOUS` | `Visibility.allows`의 입력. 값은 `social`이 계산해서 넘깁니다 |
| `GeoPoint` | 위도·경도 + 범위 검증 | `place`, `route`, `plan`, `placeimport`가 좌표를 주고받습니다 |
| `BoundingBox` | `서,남,동,북` | 지도 범위 조회 (`place`, `savedplace`, `feed`) |

공유 커널에 타입을 추가하려면 PR에 "두 개 이상의 모듈이 **같은 규칙**으로 쓴다"는 근거를 적고 모든 모듈 담당자의 리뷰를 받습니다.

---

## 5. 핵심 영역 상세 설계

### 5.1 Plan + Route + Place

#### 5.1.1 책임 분리

```
plan (일정 규칙)                     route (이동 시간)                 place (장소)
─────────────────────               ────────────────────             ─────────────
Plan · PlanItem · Reservation        TravelTimeQueryUseCase           PlaceQueryUseCase
VisitOrderOptimizer   ◀─ 행렬 ──    TravelTimeMatrix 계산             좌표 · 영업시간
ScheduleConflictChecker              DirectionsPort ──▶ 외부 길찾기
ItineraryTimeline                    TravelTimeCachePort
WeatherPort ──▶ 외부 날씨
```

| 질문 | 답 |
| --- | --- |
| 최적화 알고리즘은 어디에? | `plan/domain/policy/VisitOrderOptimizer`. 입력은 **정점 목록 + 이동 시간 표**, 출력은 순서. 외부 API · DB를 전혀 모르는 순수 함수라서 표를 직접 만들어 단위 테스트합니다 |
| 외부 길찾기 호출은 어디에? | `route/adapter/out/external/directions/`. `route`는 장소 쌍마다 이동 시간을 구해 **표(행렬)** 로 돌려줄 뿐, 순서를 정하지 않습니다 |
| 둘은 어떻게 만나나? | `plan/application/service/PlanRouteService`가 ① 일정 조회 → ② `PlaceQueryUseCase`로 좌표 조회 → ③ `TravelTimeQueryUseCase.matrix(points, mode)` → ④ 결과를 plan의 `TravelTimeTable`(VO)로 변환 → ⑤ 도메인 정책 호출 |
| 왜 `route` 결과를 plan VO로 바꾸나? | plan의 domain이 route의 타입을 import하면 모듈 경계가 domain까지 들어옵니다. 변환은 service의 몇 줄이고, 그 대가로 길찾기 응답이 바뀌어도 최적화 코드는 그대로입니다 |
| 길찾기 제공자가 바뀌면? | `route/adapter/out/external/directions/` 안만 바뀝니다. plan은 수정하지 않습니다 |
| 외부 API가 실패하면? | `route`가 직선거리 기반 추정치(`StraightLineEstimator`)로 채우고 `estimated = true`를 붙여 돌려줍니다. plan은 결과 화면에 "추정치"를 표시할 수 있습니다 |

#### 5.1.2 `plan` 상세 트리

```
plan/
├── domain/
│   ├── PlanErrorCode.java
│   ├── model/
│   │   ├── Plan.java                       # 애그리거트 루트. 소유자, 날짜, 상태(PLANNING → UPCOMING → DONE, DONE도 수정 가능)
│   │   │                                   # addItem · removeItem · reorder(전체 id 목록을 받아 0..n-1로 재배치) · complete
│   │   ├── PlanItem.java                   # 순서, 고정 시간, 방문 여부·시각, 일정 외 방문(is_extra). 같은 일정에 같은 장소 1번
│   │   └── Reservation.java                # PlanItem당 0..1. 시작·종료 시각, 인원, 예약 번호
│   ├── vo/
│   │   ├── PlanStatus.java
│   │   ├── TimeAnchor.java                 # "이 장소는 HH:mm에 도착해야 함" (고정 시간 또는 예약 시작)
│   │   ├── StayDuration.java               # 체류 시간 (값 출처 미정, 8.1 #2)
│   │   └── TravelTimeTable.java            # 장소 쌍 → 분 · 추정 여부. route 결과를 plan의 말로 바꾼 것
│   └── policy/
│       ├── ItineraryTimeline.java          # 순서 + 표 + 체류 시간 + 고정 시간 → 장소별 도착·출발 시각
│       ├── ScheduleConflictChecker.java    # 고정 시간 순서 역전, 고정 시간 사이 이동·체류 시간 부족 → 경고 목록(막지 않음)
│       └── VisitOrderOptimizer.java        # 이동 시간 합 최소화. 고정 시간이 있는 장소는 시간 순서 유지
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── PlanCommandUseCase.java     # 일정 생성·수정·삭제, 장소 추가·수정·삭제·순서 변경, 예약, 방문
│   │   │   ├── PlanQueryUseCase.java       # 내 목록, 상세, 사용자 공개 일정, 날씨
│   │   │   └── PlanRouteUseCase.java       # 경로 미리보기, 순서 최적화 추천 (외부 호출이 있어 분리)
│   │   └── out/
│   │       ├── PlanPersistencePort.java    # Plan 애그리거트 전체(items · reservations) 조회·저장·삭제, 소유자 확인용 조회
│   │       ├── PlanQueryPort.java          # 목록 · 상세 화면 (장소 이름·사진 JOIN)
│   │       └── WeatherPort.java            # 날짜 + 좌표 → 예보
│   ├── service/
│   │   ├── PlanCommandService.java         # 방문 처리 시 SavedPlaceCommandUseCase.markVisited 호출 (같은 트랜잭션)
│   │   ├── PlanQueryService.java
│   │   └── PlanRouteService.java           # 5.1.1의 ①~⑤. DB에 쓰지 않음 → 트랜잭션 readOnly, 외부 호출은 트랜잭션 밖
│   └── dto/
│       ├── CreatePlanCommand.java · UpdatePlanCommand.java · PlanItemCommand.java · ReorderCommand.java · ReservationCommand.java
│       └── PlanDetailResult.java · PlanSummaryResult.java · RoutePreviewResult.java · RouteSuggestionResult.java · WeatherResult.java
└── adapter/
    ├── in/web/
    │   ├── PlanController.java             # /plans, /plans/{planId}, /weather
    │   ├── PlanItemController.java         # /items/**, /reservation, /visit
    │   ├── PlanRouteController.java        # /route-preview, /route-optimizations
    │   ├── UserPlanController.java         # /users/{username}/plans
    │   ├── request/ · response/
    ├── out/persistence/
    │   ├── PlanPersistenceAdapter.java · PlanQueryAdapter.java
    │   ├── entity/                         # PlanJpaEntity, PlanItemJpaEntity, PlanItemReservationJpaEntity
    │   ├── repository/
    │   └── mapper/PlanMapper.java          # 자식 2단계 애그리거트라 별도 Mapper
    └── out/external/weather/WeatherApiAdapter.java   # 제공자 미정. 응답 DTO는 이 패키지 밖으로 나가지 않음
```

**왜 이 계층인가**
- `Plan.reorder()`가 도메인에 있는 이유: "모든 항목이 정확히 한 번씩 포함", "0부터 연속 번호" 같은 불변식은 화면·API가 바뀌어도 같아야 합니다.
- `ScheduleConflictChecker`가 policy인 이유: 일정(Plan)만으로 판정할 수 없고 이동 시간 표라는 **외부에서 온 값**이 필요합니다. 모델 메서드로 두면 Plan이 이동 시간을 필드로 가져야 합니다.
- `PlanRouteUseCase`를 분리한 이유: 외부 호출이 있는 유일한 기능이라 실패·지연·비용 특성이 다릅니다. 테스트에서 이 서비스만 Fake `TravelTimeQueryUseCase`가 필요합니다.
- 최적화 결과를 저장하지 않는 이유: 명세서상 "추천"입니다. 사용자가 받아들이면 프론트가 `PUT /items/reorder`로 저장합니다. 그래서 `route-optimizations`는 DB를 바꾸지 않고 여러 번 호출해도 같습니다.

#### 5.1.3 고정 · 유동 장소와 충돌 검증

| 개념 | 판정 |
| --- | --- |
| 고정 장소 | `fixed_time`이 있음. 없고 예약 `start_time`이 있으면 그 값 (둘 중 무엇이 우선인지 미정, 8.5 #2) |
| 유동 장소 | 고정 시간이 없음. 최적화 때 위치가 바뀔 수 있음 |
| 출발 시각 | 고정 장소: `max(도착, 고정 시각) + 체류`, 예약 `end_time`이 있으면 그 시각 |

`plans`에 하루 시작 시각 컬럼이 없으므로, 검증은 **고정 장소를 기준점으로 구간을 나눠** 합니다.

```
[유동 A] → [고정 B 12:00] → [유동 C] → [유동 D] → [고정 E 15:00]
            └──────── 구간: B 출발 ~ E 도착 ────────┘
필요 시간 = 이동(B→C) + 체류(C) + 이동(C→D) + 체류(D) + 이동(D→E)
필요 시간 > (15:00 − B 출발)  →  경고 NOT_ENOUGH_TIME
```

| 경고 | 조건 |
| --- | --- |
| `FIXED_TIME_ORDER` | 목록 순서상 뒤에 있는 고정 장소가 시간은 더 이름 |
| `NOT_ENOUGH_TIME` | 두 고정 장소 사이 필요 시간이 간격보다 김 |
| `ESTIMATED_TRAVEL_TIME` | 이동 시간 일부가 추정치 |
| `STAY_TIME_UNKNOWN` | 체류 시간을 알 수 없어 0으로 계산함 (8.1 #2가 정해질 때까지) |

경고는 저장·변경을 **막지 않습니다** (프론트 9.3의 "막지 않고 안내만"과 같은 원칙). 영업시간 충돌은 일정 자동 점검 보류(common 부록 A.7)에 포함되므로 지금은 넣지 않고, 보류를 풀면 `PlaceQueryUseCase`가 `OpeningHoursPolicy` 결과를 제공합니다.

#### 5.1.4 순서 최적화 알고리즘

| 장소 수 | 방법 | 이유 |
| --- | --- | --- |
| ≤ 9 (유동 장소 기준) | 고정 장소 순서를 유지한 상태에서 유동 장소 배치 완전 탐색 (또는 Held-Karp DP) | 하루 일정은 대부분 이 범위. 결과가 항상 최적이라 설명하기 쉬움 |
| > 9 | 최근접 이웃으로 시작 → 2-opt 개선, 고정 장소 순서 제약 유지 | 계산량 제한 |

- 입력 장소 수 상한과 외부 API 호출 수 상한(장소 n개면 n×(n−1)쌍)을 설정값으로 둡니다. 상한을 넘으면 `route`가 직선거리 추정치로 채웁니다.
- 결과에는 순서와 함께 **이전 대비 이동 시간 차이**, 남은 경고를 돌려줍니다.

#### 5.1.5 `route` 상세 트리

```
route/
├── domain/
│   ├── vo/
│   │   ├── TravelMode.java                 # 명세서 mode 값 (값 목록 미정, 8.7)
│   │   ├── TravelLeg.java                  # 분 · 미터 · 추정 여부
│   │   └── TravelMatrix.java               # 정점 n개의 n×n TravelLeg
│   └── policy/StraightLineEstimator.java   # 하버사인 거리 ÷ 수단별 평균 속도 (외부 실패·상한 초과 시)
├── application/
│   ├── port/in/TravelTimeQueryUseCase.java # between(placeA, placeB, mode) — API용
│   │                                       # matrix(List<GeoPoint>, mode) — plan용
│   ├── port/out/
│   │   ├── DirectionsPort.java             # 외부 길찾기 (구간 또는 행렬). 행렬 API가 없는 제공자면 어댑터가 구간 호출을 묶음
│   │   └── TravelTimeCachePort.java        # (출발, 도착, 수단) → TravelLeg
│   ├── service/TravelTimeQueryService.java # 캐시 → 외부 → 실패 시 추정치. 트랜잭션 없음
│   └── dto/TravelTimeResult.java · TravelMatrixResult.java
└── adapter/
    ├── in/web/TravelTimeController.java    # GET /travel-times
    └── out/external/
        ├── directions/DirectionsApiAdapter.java    # 제공자 확정 후 이름 변경. 수단마다 제공자가 다르면 그때 수단별 어댑터 + 선택 로직
        └── cache/InMemoryTravelTimeCacheAdapter.java   # Caffeine. 서버가 여러 대가 되면 공유 캐시로 교체
```

`route`는 테이블이 없으므로 `persistence`가 없습니다. 장소 id로 받는 API(`/travel-times`)를 위해 `place`의 `PlaceQueryUseCase`만 호출합니다.

---

### 5.2 Collection + Payment + Settlement

#### 5.2.1 모듈 협력 방식

```
          ┌─────────────────────── collection ───────────────────────┐
          │  CollectionPaymentHandler (adapter/in/payment)            │
          │      implements payment.PaymentPurposeHandler   ◀────────┼──── payment가 호출 (SPI)
          │  PurchaseRefundService ─────────────────────────────────┼──▶ payment.RefundPaymentUseCase
          │  SellerQueryUseCase 호출 ───────────────────────────────┼──▶ creator
          └───────────────────────────────────────────────────────────┘
settlement ──▶ collection.PurchaseSettlementUseCase, creator.PayoutAccountUseCase
```

| 협력 | 방식 | 이벤트로 하지 않는 이유 |
| --- | --- | --- |
| 결제 준비 시 금액 · 대상 확인 | payment → `PaymentPurposeHandler.prepare` (SPI, 동기) | 결과(금액)가 지금 필요 |
| 승인 후 구매 권한 부여 | payment → `PaymentPurposeHandler.onApproved` (SPI, **같은 트랜잭션**) | 결제 APPROVED와 구매 생성은 함께 성공하거나 함께 실패해야 함. 이벤트는 커밋 후 유실 가능 |
| 환불 신청 | collection → `RefundPaymentUseCase.refund` (동기) | 가능 판정과 실행이 한 요청 안에서 끝나야 함 |
| 환불 완료 후 구매 회수 | payment → `PaymentPurposeHandler.onRefunded` (SPI, 같은 트랜잭션) | 위와 같음 |
| 구매 알림 · 환불 알림 | `CollectionPurchasedEvent`, `PurchaseRefundedEvent` → notification (커밋 후) | 실패해도 구매는 유효 |
| 크리에이터 박탈 시 판매 처리 | `CreatorSuspendedEvent` → collection (정책 미정, 8.5 #5) | creator가 collection을 부르면 순환 |

**SPI(`PaymentPurposeHandler`)로 순환을 끊는 방법:** 인터페이스는 `payment/application/port/out`에 있고(payment가 "결제 목적별 처리"를 바깥에 요구), 구현은 `collection/adapter/in/payment`에 있습니다. 컴파일 의존은 `collection → payment` 한 방향뿐이고, payment는 "컬렉션"이라는 단어를 모릅니다. Plus 구독이 생기면 그 모듈이 구현체를 하나 더 둡니다.

#### 5.2.2 `collection` 상세 트리

```
collection/
├── domain/
│   ├── CollectionErrorCode.java
│   ├── model/
│   │   ├── Collection.java                 # 애그리거트 루트: 제목·설명·공개 범위·핀·노출·표지·가격 + 구성 장소 목록
│   │   │                                   # changePrice(price, sellerEligible) · addPlace · removePlace · reorder · contentFingerprint()
│   │   ├── CollectionPlace.java            # 저장 장소 id, 장소 id, 순서 (앞 2곳 = 미리보기)
│   │   ├── CollectionVersion.java          # 별도 애그리거트. 버전 번호, 제목·설명·장소 수, 장소 목록(이름 포함) — 추가만
│   │   └── CollectionPurchase.java         # 별도 애그리거트. 금액·수수료율·수수료, ACTIVE/REFUNDED, 첫 열람, 버전, 결제, 정산
│   │                                       # markFirstViewed(now) · refund() · linkSettlement(id)
│   ├── vo/
│   │   ├── CollectionPrice.java            # null(무료) 또는 500~50,000 · 100원 단위
│   │   ├── PurchaseStatus.java
│   │   └── PinStyle.java
│   ├── policy/
│   │   ├── CollectionAccessPolicy.java     # (공개 범위 허용?, 가격, 소유자?, 구매 상태) → FULL / PREVIEW(앞 2곳) / NONE
│   │   ├── RefundWindowPolicy.java         # 환불 가능 = ACTIVE · 첫 열람 전 · 기한 내 · 정산 전. 정산 가능 = 그 반대 조건
│   │   │                                   # 환불과 정산이 같은 기준을 써야 하므로 한 클래스에 둠
│   │   └── VersionSnapshotPolicy.java      # 최신 버전과 현재 내용(fingerprint)이 다르면 새 버전 필요
│   └── event/
│       ├── CollectionPurchasedEvent.java
│       └── PurchaseRefundedEvent.java
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── CollectionCommandUseCase.java    # 생성·수정·삭제, 장소 추가·수정·제거·순서
│   │   │   ├── CollectionQueryUseCase.java      # 내 목록, 상세(권한별), 탐색, 사용자 공개 목록, 버전 목록·상세
│   │   │   ├── PurchaseQueryUseCase.java        # 내 구매 목록·상세, 스냅샷 열람(첫 열람 기록)
│   │   │   ├── PurchaseRefundUseCase.java       # 환불 신청
│   │   │   ├── PurchaseGrantUseCase.java        # 결제 SPI 어댑터 전용: 구매 가능 확인 · 권한 부여 · 회수
│   │   │   └── PurchaseSettlementUseCase.java   # settlement 전용: 정산 대상 조회 · 정산 연결
│   │   └── out/
│   │       ├── CollectionPersistencePort.java   # Collection + CollectionPlace
│   │       ├── CollectionVersionPersistencePort.java
│   │       ├── PurchasePersistencePort.java     # findByIdForUpdate(행 잠금), markFirstViewedIfAbsent(조건부 UPDATE)
│   │       └── CollectionQueryPort.java         # 상세·탐색·목록 (잠금 반영, savedByMe JOIN)
│   ├── service/
│   │   ├── CollectionCommandService.java        # 가격 설정 시 SellerQueryUseCase로 판매 자격 확인
│   │   ├── CollectionQueryService.java          # RelationQueryUseCase → ViewerRelation → CollectionAccessPolicy
│   │   ├── PurchaseQueryService.java
│   │   ├── PurchaseRefundService.java
│   │   ├── PurchaseGrantService.java            # 5.2.4 tx2 안에서 실행
│   │   └── PurchaseSettlementService.java
│   └── dto/
│       ├── CreateCollectionCommand.java · UpdateCollectionCommand.java · CollectionItemCommand.java …
│       ├── PurchaseGrantCommand.java · RefundRequestCommand.java
│       └── CollectionDetailResult.java (accessLevel, lockedPlaceCount 포함) · PurchaseResult.java · PurchaseSnapshotResult.java · SettleablePurchaseResult.java
└── adapter/
    ├── in/web/
    │   ├── CollectionController.java · CollectionItemController.java · UserCollectionController.java
    │   ├── PurchaseController.java          # /me/purchases/**
    │   ├── RefundController.java            # POST /purchases/{purchaseId}/refunds
    │   ├── request/ · response/
    ├── in/payment/CollectionPaymentHandler.java    # PaymentPurposeHandler 구현 → PurchaseGrantUseCase 호출만
    ├── in/event/
    │   ├── SavedPlaceDeletingListener.java  # 같은 트랜잭션: 구성 장소 제거 · place_count 갱신
    │   ├── SavedPlaceCollectionsChangedListener.java  # 같은 트랜잭션: 저장하면서 고른 컬렉션에 담기 · 빼기 (8.1 #13)
    │   └── CreatorSuspendedListener.java    # 정책 확정 후 구현
    └── out/persistence/
        ├── entity/                          # CollectionJpaEntity, CollectionPlaceJpaEntity, CollectionVersionJpaEntity,
        │                                    # CollectionVersionPlaceJpaEntity, CollectionPurchaseJpaEntity
        ├── repository/ · mapper/CollectionMapper.java · mapper/CollectionVersionMapper.java
        ├── *PersistenceAdapter.java
        └── query/CollectionQueryAdapter.java     # places를 JOIN하는 조회 SQL (6.1)
```

**왜 이 계층인가**
- `CollectionPurchase`를 Collection과 **다른 애그리거트**로 둔 이유: 구매는 수천 건이 될 수 있고, 구매 하나를 만들 때 컬렉션 전체를 잠그면 안 됩니다. 컬렉션 id로만 참조합니다.
- `RefundWindowPolicy`가 collection에 있는 이유: 환불 가능 기준과 정산 포함 기준은 **같은 조건의 반대**입니다. 한쪽만 바뀌면 "환불됐는데 정산도 된" 구매가 생깁니다. settlement는 이 판정을 `PurchaseSettlementUseCase`로 받기만 합니다.
- `CollectionPaymentHandler`가 `adapter/in`인 이유: payment 모듈이 **호출해 들어오는** 입구라서 HTTP 컨트롤러와 같은 위치입니다. 변환만 하고 규칙은 `PurchaseGrantService`에 있습니다.

#### 5.2.3 `payment` 상세 트리

```
payment/
├── domain/
│   ├── PaymentErrorCode.java
│   ├── model/
│   │   ├── Payment.java                    # READY → APPROVING → APPROVED | FAILED (상태 값은 제안, 8.7)
│   │   │                                   # startApproval(amount, userId): 금액·소유자 확인 · confirmApproved(pgResult) · fail(code)
│   │   └── Refund.java                     # REQUESTED → COMPLETED | FAILED, 요청자 또는 처리자 필수, COMPLETED면 취소 키 필수
│   └── vo/
│       ├── OrderId.java                    # 생성 규칙, 64자 이하 (멱등 키)
│       ├── PaymentStatus.java · PaymentPurpose.java · RefundReason.java
│       └── PgApproval.java                 # 승인 결과 (payment_key, method, approved_at, 원문 응답)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── PaymentUseCase.java         # prepare, confirm
│   │   │   ├── RefundPaymentUseCase.java   # refund(paymentId, reason, requestedBy | handledBy) — collection · (향후) 관리자
│   │   │   ├── PaymentQueryUseCase.java    # 내 결제 내역
│   │   │   └── PaymentReconcileUseCase.java   # 스케줄러 전용: 결과를 모르는 결제·환불 확정
│   │   └── out/
│   │       ├── PaymentPersistencePort.java # findByOrderIdForUpdate, save
│   │       ├── RefundPersistencePort.java
│   │       ├── PaymentGatewayPort.java     # approve / cancel / inquire. 결과는 성공 · 거절 · 알 수 없음(타임아웃) 세 가지
│   │       └── PaymentPurposeHandler.java  # SPI: purpose(), prepare(userId, targetId) → 금액·주문명,
│   │                                       #      onApproved(PaidOrder), onRefunded(paymentId)
│   ├── service/
│   │   ├── PaymentService.java             # confirm은 트랜잭션 없이 아래 단계를 순서대로 호출
│   │   ├── PaymentApprovalSteps.java       # @Transactional 단계 메서드: begin(tx1), complete(tx2), markFailed
│   │   │                                   # 별도 빈인 이유: 같은 클래스 안 호출은 Spring 트랜잭션 프록시를 거치지 않음
│   │   ├── RefundService.java · RefundSteps.java
│   │   ├── PaymentQueryService.java
│   │   └── PaymentReconcileService.java
│   └── dto/
│       ├── PreparePaymentCommand.java · ConfirmPaymentCommand.java · RefundCommand.java
│       └── PreparedPaymentResult.java · PaymentResult.java · PaidOrder.java(SPI로 넘기는 승인 정보)
└── adapter/
    ├── in/web/PaymentController.java       # /payments/prepare, /payments/confirm, /me/payments (+ request/ · response/)
    ├── in/scheduler/PaymentReconcileScheduler.java   # APPROVING · REQUESTED가 N분 넘게 남은 건 PG 조회
    ├── out/persistence/
    └── out/external/pg/
        ├── TestPaymentGatewayAdapter.java  # 지금: 테스트 결제 (local · test 프로필)
        └── PgPaymentGatewayAdapter.java    # PG 확정 후 구현. 승인·취소 요청에 멱등 키(orderId · refundId) 전달
```

#### 5.2.4 결제 승인과 구매 권한 확정 (정합성)

PG 호출은 느리고 실패할 수 있으므로 **DB 트랜잭션 안에서 PG를 기다리지 않습니다.** 대신 상태를 두 번 나눠 저장합니다.

```
POST /payments/prepare { purpose, targetId }
  tx: handler.prepare(userId, targetId)        ← collection: 판매 중? 이미 구매? 가격은?
      Payment(READY, orderId, amount=서버 가격) 저장  → orderId · amount 반환

POST /payments/confirm { orderId, paymentKey, amount }
  tx1 (PaymentApprovalSteps.begin)
      SELECT … FOR UPDATE (order_id)
      ├─ APPROVED      → 기존 결과 그대로 반환 (멱등: 더블클릭 · 재시도)
      ├─ APPROVING     → 409 PAYMENT_IN_PROGRESS
      ├─ amount ≠ 저장 금액 · userId ≠ 소유자 → 거절 (PG 호출 안 함)
      └─ READY → APPROVING 으로 변경, 커밋
  PG 승인 (트랜잭션 밖, 멱등 키 = orderId)
      ├─ 거절        → tx: FAILED(실패 코드·메시지)
      ├─ 알 수 없음   → APPROVING 유지 → 대사 스케줄러가 PG 조회로 확정
      └─ 성공 ↓
  tx2 (PaymentApprovalSteps.complete)
      Payment APPROVED (payment_key, approved_at, 원문 응답)
      handler.onApproved(paidOrder)            ← collection: 버전 확정 · 수수료율 복사 · 구매 생성 (같은 트랜잭션)
      커밋 후 CollectionPurchasedEvent → notification
  tx2 실패 (예: 동시에 다른 주문으로 이미 구매 → UNIQUE 위반)
      → PG 취소(보상) → tx: 결제 FAILED + 사유
      → 취소도 실패하면 APPROVING 유지 → 대사 스케줄러
```

| 위험 | 막는 방법 |
| --- | --- |
| 같은 주문 중복 승인 | `order_id` UNIQUE + 행 잠금 + 상태 조건 + PG 멱등 키 |
| 금액 위변조 | 서버가 prepare 때 저장한 금액과 비교 (프론트 금액은 믿지 않음) |
| 중복 구매 | prepare 단계 확인 + `UNIQUE(user_id, collection_id) WHERE status = 'ACTIVE'` 최종 방어 + 보상 취소 |
| 승인됐는데 구매가 없음 | tx2에서 결제 APPROVED와 구매 생성을 한 트랜잭션으로 |
| PG 응답 유실 · 서버 중단 | DB 상태(APPROVING)가 남으므로 `PaymentReconcileScheduler`가 PG 조회로 마무리 |
| prepare와 confirm 사이 가격 변경 | 미정 (8.5 #3). 제안: tx2에서 handler가 현재 가격과 다르면 보상 취소 + `PRICE_CHANGED` |

#### 5.2.5 버전 스냅샷 · 첫 열람 · 환불

**버전 확정 (구매 시):** `PurchaseGrantService`가 `VersionSnapshotPolicy`로 최신 버전과 현재 내용을 비교해 다르면 새 `collection_versions` · `collection_version_places`를 만들고, 구매의 `collection_version_id`를 그 버전으로 정합니다. 수수료율은 `SellerQueryUseCase`로 받은 현재 등급 값을 `fee_rate`에 복사합니다 (이후 등급이 바뀌어도 그대로).
스냅샷이 보존하는 범위에 빈 곳이 있습니다 (8.4 #1).

**첫 열람 기록:**
- 잠긴 장소(미리보기 2곳 외)를 **처음 응답에 담는 순간** `first_viewed_at`을 기록합니다. 조건부 UPDATE(`WHERE first_viewed_at IS NULL`)라 여러 번 불러도 처음 시각만 남습니다.
- GET 요청이 상태를 바꾸므로, 링크 미리 가져오기(prefetch)나 미리보기 봇이 환불 자격을 없애지 않도록 **어느 API가 열람을 기록하는지** 정해야 합니다 (8.5 #4).

**환불 (`POST /purchases/{purchaseId}/refunds`):**
```
tx1  PurchaseRefundService
     구매 SELECT … FOR UPDATE                     ← 같은 구매의 열람 기록 · 정산 연결과 직렬화
     RefundWindowPolicy: ACTIVE · 첫 열람 전 · 기한 내 · 정산 전?
     RefundPaymentUseCase.refund → Refund(REQUESTED) 저장, 커밋
PG 취소 (트랜잭션 밖, 멱등 키 = refund id)
tx2  RefundSteps.complete: Refund COMPLETED(pg_cancel_key, completed_at)
     handler.onRefunded → 구매 REFUNDED
     커밋 후 PurchaseRefundedEvent → notification
```
REQUESTED 상태인 동안은 잠긴 장소 열람을 막습니다 (제안: 환불 판정 이후 열람으로 콘텐츠를 가져가는 것 방지).

#### 5.2.6 `settlement` 상세 트리

```
settlement/
├── domain/
│   ├── model/Settlement.java               # PENDING → CONFIRMED → PAID, HELD(사유). 크리에이터·월당 1개
│   ├── vo/
│   │   ├── SettlementPeriod.java           # 월 단위 (1일 ~ 말일)
│   │   └── PayoutAccountSnapshot.java      # 은행 · 예금주 · 뒤 4자리만
│   └── policy/
│       ├── SettlementCalculator.java       # 합계 − 수수료 − 원천징수(개인 3.3%) = 지급액(≥ 0). 원 단위 처리 규칙 미정 (8.5 #6)
│       └── SettlementHoldPolicy.java       # 계좌 없음 · 미인증 · 주민번호 없음 · 크리에이터 정지 → HELD
├── application/
│   ├── port/in/
│   │   ├── SettlementQueryUseCase.java     # 내 정산 목록 · 상세
│   │   ├── SettlementAdminUseCase.java     # 정산 확정 (관리자 작업 기록 포함)
│   │   └── SettlementAggregationUseCase.java   # 월 집계 (스케줄러)
│   ├── port/out/SettlementPersistencePort.java · SettlementQueryPort.java
│   ├── service/
│   │   ├── SettlementAggregationService.java   # 크리에이터마다 별도 트랜잭션 (한 명 실패가 전체를 막지 않음)
│   │   ├── SettlementAdminService.java · SettlementQueryService.java
│   └── dto/
└── adapter/
    ├── in/web/SettlementController.java            # /me/creator/settlements/**
    ├── in/web/admin/AdminSettlementController.java # /admin/settlements/{settlementId}/confirm
    ├── in/scheduler/MonthlySettlementScheduler.java
    └── out/persistence/
```

**집계 한 건의 흐름:** `PurchaseSettlementUseCase.findSettleable(creatorId, 기준 시각)` (행 잠금, `settlement_id IS NULL`, `RefundWindowPolicy` 정산 가능) → `SettlementCalculator` → `PayoutAccountUseCase.snapshot` → `SettlementHoldPolicy` → 저장 (`UNIQUE(creator_id, period_start)`로 재실행해도 중복 없음) → `linkSettlement` (조건부 UPDATE).

---

### 5.3 PlaceImport

#### 5.3.1 교체 가능한 외부 어댑터

| 단계 | 포트 | 어댑터 | 교체되는 경우 |
| --- | --- | --- | --- |
| 1. 링크 수집 | `WebContentFetchPort` | `external/fetch/GenericWebFetchAdapter`, `NaverBlogFetchAdapter` (+ 플랫폼별 추가) | 플랫폼 추가 · 수집 방식 변경 |
| 2. 본문 분석 | (domain) `ContentNormalizer` | — | 순수 로직: 광고 문구 · 중복 공백 제거, 길이 제한 |
| 3. 장소 후보 추출 | `PlaceCandidateExtractorPort` | `external/llm/LlmPlaceExtractorAdapter` | LLM 제공자 · 모델 · 프롬프트 변경 |
| 4. 장소 검증 | **`place` 모듈의 `PlaceSearchUseCase`** | (place 안의 `KakaoLocalSearchAdapter`) | 지도 제공자 변경 → `place` 어댑터만 교체 |

장소 검증에 별도 지도 어댑터를 두지 않는 이유: 지도 검색은 장소 검색 API(`/places/search`)와 같은 기능입니다. 지도 API 연동을 `place` 한 곳에 두면 키 · 호출 제한 · 응답 변환도 한 곳에서 관리됩니다.

#### 5.3.2 상세 트리

```
placeimport/
├── domain/
│   ├── PlaceImportErrorCode.java
│   ├── model/
│   │   └── ImportJob.java                  # 상태(RUNNING → SUCCEEDED | FAILED | CANCELED, 값은 제안), 단계(0~4), 실패 코드·사유
│   │                                       # advanceTo(step) · succeed() · fail(failure) · cancel() — 끝난 작업은 바뀌지 않음
│   ├── vo/
│   │   ├── ImportSource.java               # URL | TEXT, 원본 값, 정규화 URL
│   │   ├── SourcePlatform.java             # WEB, NAVER_BLOG … (값 목록 미정)
│   │   ├── ImportStep.java                 # FETCH(1) · EXTRACT(2) · MATCH(3) · FINALIZE(4) ↔ total_steps = 4
│   │   ├── ImportFailure.java              # 코드 · 사유 · 재시도 가능 여부
│   │   └── ExtractedCandidate.java         # 추출 이름 · 주소 · 근거 문장
│   ├── policy/
│   │   ├── UrlNormalizer.java              # 추적 파라미터 제거, 모바일 → PC 주소 (캐시 키)
│   │   ├── ContentNormalizer.java
│   │   ├── CandidateMatchPolicy.java       # 이름 유사도 · 주소/지역 일치 · 거리 → 일치도(0~1), 기준 미만이면 "확인 필요"
│   │   ├── CandidateDeduplicationPolicy.java   # 같은 장소로 매칭된 후보 합치기, 이미 저장한 장소 표시
│   │   └── ImportRetryPolicy.java          # 재시도할 실패(시간 초과 · 429)와 아닌 것(차단 · 본문 없음), 최대 횟수 · 대기
│   └── event/ImportJobRequestedEvent.java  # 모듈 내부용 (커밋 후 비동기 실행 시작)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── PlaceImportUseCase.java     # request · getStatus(결과 포함) · cancel
│   │   │   └── ImportJobExecutionUseCase.java   # run(jobId) — 비동기 리스너 · 복구 스케줄러만 호출
│   │   └── out/
│   │       ├── ImportJobPersistencePort.java    # 저장 · 조회 · 상태 확인 · 오래 멈춘 RUNNING 조회 · 같은 URL 최근 성공 작업
│   │       ├── ExtractionRunPort.java           # 실행 기록 추가 (2단계: 모델 없음)
│   │       ├── ImportResultPort.java            # 후보 목록 저장·조회 (저장 위치 미정, 8.1 #5)
│   │       ├── WebContentFetchPort.java         # fetch(url) → 본문 · 제목 · 최종 URL · 플랫폼
│   │       └── PlaceCandidateExtractorPort.java # extract(본문) → 후보 목록 + 모델 · 프롬프트 버전 · 토큰 · 소요 시간
│   ├── service/
│   │   ├── PlaceImportService.java         # 요청: 정규화 → 캐시 확인 → RUNNING 저장 → 이벤트 발행 / 취소: CANCELED
│   │   └── ImportJobExecutionService.java  # 5.3.3 파이프라인
│   └── dto/
│       ├── RequestImportCommand.java
│       └── ImportJobResult.java (상태 · 단계 · 실패 · 후보 목록) · ImportCandidateResult.java
└── adapter/
    ├── in/web/PlaceImportController.java
    ├── in/event/ImportJobRequestedListener.java    # @TransactionalEventListener(AFTER_COMMIT) + @Async("importExecutor")
    ├── in/scheduler/StaleImportJobRecoveryScheduler.java   # updated_at이 오래된 RUNNING → 재실행 또는 TIMEOUT 실패
    ├── out/persistence/
    └── out/external/
        ├── fetch/
        │   ├── SafeHttpClient.java          # 사설 IP · 내부 주소 · 리다이렉트 대상 차단(SSRF), 크기 · 시간 제한
        │   ├── GenericWebFetchAdapter.java
        │   ├── NaverBlogFetchAdapter.java   # 본문 iframe 처리
        │   └── RoutingWebContentFetchAdapter.java   # 플랫폼에 따라 위 어댑터 선택 (구현이 둘 이상이라 필요)
        └── llm/
            ├── LlmPlaceExtractorAdapter.java    # 제공자 SDK 호출, JSON 스키마 형식 출력, 토큰 사용량 · 비용 계산
            └── LlmProperties.java               # 모델, 시간 제한, 모델별 단가 (estimated_cost_usd 계산)
```

프롬프트는 `resources/prompts/place-import/v{n}.md`에 두고, 어댑터가 사용한 버전을 결과에 담아 돌려줍니다. 프롬프트를 바꿀 때는 기존 파일을 수정하지 않고 새 버전 파일을 추가해서 `import_extraction_runs.prompt_version`으로 결과를 비교할 수 있게 합니다.

#### 5.3.3 실행 · 실패 · 재시도 · 취소

```
ImportJobExecutionService.run(jobId)           트랜잭션은 단계 기록할 때만 짧게
  ① FETCH    tx: step=1 → WebContentFetchPort.fetch   (트랜잭션 밖)
  ② EXTRACT  tx: step=2 → ContentNormalizer → PlaceCandidateExtractorPort.extract → ExtractionRunPort.record
  ③ MATCH    tx: step=3 → 후보마다 PlaceSearchUseCase → CandidateMatchPolicy → CandidateDeduplicationPolicy
             (이미 저장한 장소 확인: SavedPlaceQueryUseCase)
  ④ FINALIZE tx: ImportResultPort.save + succeed()
  각 단계 시작 전: 작업이 CANCELED면 중단 (결과 버림)
  실패: ImportRetryPolicy → 재시도 가능하면 대기 후 같은 단계 재실행, 아니면 fail(failure)
```

| 상황 | 처리 |
| --- | --- |
| 취소 | `DELETE`가 상태를 CANCELED로 바꿈. 실행 중인 작업은 다음 단계 시작 전에 확인하고 멈춤 (진행 중인 외부 호출은 끝까지 기다린 뒤 결과를 버림) |
| 서버 재시작으로 작업 유실 | DB 상태가 원본. `StaleImportJobRecoveryScheduler`가 오래 멈춘 RUNNING을 찾아 재실행 |
| 같은 링크 반복 요청 | 정규화 URL이 같은 최근 성공 작업이 있으면 결과를 재사용하고 `cache_hit = true` 기록 |
| 비용 폭주 | 회원당 동시 실행 수 · 하루 요청 수 제한 (설정값), 본문 길이 상한 |
| 재시도 횟수 저장 | `import_jobs`에 재시도 컬럼이 없음 → 지금은 메모리 안 재시도만, 컬럼 추가는 제안 (8.1 #5) |

---

### 5.4 Conversation

#### 5.4.1 전송 계층과 업무 로직 분리

```
HTTP  ConversationController ─┐                         ┌─▶ ConversationPersistencePort / MessagePersistencePort
                              ├─▶ MessageUseCase ───────┤
STOMP DmStompController ──────┘   (MessageService)      ├─▶ RelationQueryUseCase (차단)
      @MessageMapping("dm.send")                        ├─▶ MediaQueryUseCase / PlaceQueryUseCase / FeedQueryUseCase (첨부 확인)
      @MessageMapping("dm.read")                        └─▶ MessagePushPort ──▶ StompMessagePushAdapter ──▶ /user/queue/dm
```

| 전송 계층(adapter)이 하는 일 | 업무 로직(application · domain)이 하는 일 |
| --- | --- |
| STOMP 프레임 → `SendMessageCommand` 변환, Principal → 회원 id | 참여자인지, 나간 대화방인지, 1:1에서 차단 관계인지 |
| 오류를 `/user/queue/dm/errors`로 보내기 (`@MessageExceptionHandler`) | 본문 2,000자 · 첨부 1개 규칙, 첨부를 볼 수 있는지 |
| HTTP는 응답 본문으로 결과 반환 | 메시지 저장, 대화방 마지막 메시지 갱신, 보낸 사람 읽음 위치 갱신 |
| `SimpMessagingTemplate` 사용 (out 어댑터 안에서만) | 누구에게 보낼지 결정 → `MessagePushPort` 호출 |

HTTP와 STOMP가 **같은 `MessageUseCase`** 를 호출하므로 규칙은 한 곳에만 있습니다. 읽음 처리도 `PATCH /read`와 `/app/dm.read`가 같은 `markRead`를 씁니다.

#### 5.4.2 상세 트리

```
conversation/
├── domain/
│   ├── ConversationErrorCode.java
│   ├── model/
│   │   ├── Conversation.java               # DIRECT | GROUP, 참여자 목록. openDirect(a, b) · createGroup · leave
│   │   │                                   # ensureActiveMember(userId) — 나간 참여자는 보내기 · 읽기 불가
│   │   ├── ConversationMember.java         # 참여 · 나감, 마지막 읽은 메시지 (뒤로 가지 않음)
│   │   └── Message.java                    # 본문 또는 첨부 하나, soft delete
│   ├── vo/
│   │   ├── DirectKey.java                  # "작은id:큰id" — 1:1 대화방 중복 방지 키
│   │   ├── ConversationType.java
│   │   └── MessageAttachment.java          # POST | PLACE | IMAGE + 대상 id
│   └── event/MessageSentEvent.java         # notification용 (DM 알림을 만들지는 미정)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── ConversationCommandUseCase.java  # 1:1 열기(있으면 기존), 그룹 만들기, 나가기
│   │   │   ├── ConversationQueryUseCase.java    # 목록(마지막 메시지 · 안 읽은 수), 상세, 메시지 내역(커서)
│   │   │   └── MessageUseCase.java              # send, markRead — HTTP · STOMP 공용
│   │   └── out/
│   │       ├── ConversationPersistencePort.java # Conversation + Members
│   │       ├── MessagePersistencePort.java
│   │       ├── ConversationQueryPort.java
│   │       └── MessagePushPort.java             # pushMessage(받는 사람들, 메시지), pushRead(받는 사람들, 읽음 정보)
│   ├── service/
│   │   ├── ConversationCommandService.java      # DIRECT 동시 생성: direct_key UNIQUE 위반 시 기존 대화방 반환
│   │   ├── ConversationQueryService.java
│   │   └── MessageService.java
│   └── dto/
│       ├── SendMessageCommand.java (clientMessageId 포함, 8.1 #10) · MarkReadCommand.java · OpenDirectCommand.java …
│       └── MessageResult.java · ReadReceiptResult.java · ConversationSummaryResult.java
└── adapter/
    ├── in/web/ConversationController.java  # + request/ · response/
    ├── in/websocket/
    │   ├── DmStompController.java          # @MessageMapping("dm.send" · "dm.read"), @MessageExceptionHandler
    │   └── payload/                        # SendMessagePayload, ReadPayload, DmErrorPayload (STOMP 전용 DTO)
    ├── out/persistence/
    └── out/websocket/StompMessagePushAdapter.java   # 커밋 후 전송 (TransactionSynchronization) → convertAndSendToUser
```

#### 5.4.3 연결 인증 · 권한

| 단계 | 위치 | 내용 |
| --- | --- | --- |
| 연결 (`/ws` 핸드셰이크) | `auth/adapter/in/websocket/WebSocketAuthHandshakeInterceptor` | httpOnly 쿠키의 Access Token 검증 → Principal(회원 id). JS가 토큰을 읽을 수 없으므로 CONNECT 헤더 대신 핸드셰이크 쿠키를 씀 |
| 구독 (SUBSCRIBE) | `auth/adapter/in/websocket/StompDestinationGuard` | `/user/queue/**` 외 목적지 구독 거부. 사용자 목적지는 본인에게만 전달되므로 대화방별 구독 권한 검사가 필요 없음 |
| 보내기 (SEND) | `MessageService` | 대화방 참여 · 차단 · 첨부 권한 검사 (HTTP와 같은 코드) |
| 토큰 만료 · 정지 | 정책 미정 (8.5 #7) | 제안: 보낼 때마다 회원 상태 확인, 연결은 Access Token 만료 시각에 끊고 재연결 |

**전달 시점:** 메시지는 DB 커밋 후에 보냅니다. 커밋 전에 보내면 롤백된 메시지가 상대 화면에 나타날 수 있습니다. 서버가 여러 대가 되면 simple broker를 외부 브로커 relay로 바꾸며, 이때 바뀌는 곳은 `common/websocket`과 `StompMessagePushAdapter`뿐입니다.

---

## 6. 모듈 간 의존 · 이벤트 · 트랜잭션

### 6.1 다른 모듈 참조 기준

| 대상 | 허용 | 위치 |
| --- | --- | --- |
| 다른 모듈의 **domain 모델 · vo · policy** | ❌ 금지 | 공유 커널(`common/domain`)만 예외 |
| 다른 모듈의 **in-port + application/dto** | ✅ application service에서 | "공개 API". 결과가 지금 필요할 때 |
| 다른 모듈의 **domain/event** | ✅ `adapter/in/event` 리스너에서 | domain · service는 다른 모듈 이벤트를 import하지 않음 |
| 다른 모듈의 **out-port 구현** | ⚠️ 지정된 SPI만 | `payment.PaymentPurposeHandler` ← `collection/adapter/in/payment` |
| 다른 모듈의 **JPA Repository · Entity · Adapter · Service** | ❌ 금지 | ArchUnit으로 검사 |
| 다른 모듈 **테이블 쓰기** | ❌ 금지 | 2.3의 포트로 요청 |
| 다른 모듈 **테이블 읽기 JOIN** | ⚠️ 조회 어댑터(`*QueryAdapter`)의 SQL에서만 | 아래 |

**조회용 JOIN 예외:** 피드 카드 한 장에 작성자 · 장소 · `likedByMe` · `savedByMe`가 필요합니다. 모듈마다 in-port를 호출하면 N+1이 되므로, `*QueryAdapter`는 **읽기 전용 SQL로 다른 모듈 테이블을 JOIN할 수 있습니다.**
- 다른 모듈의 JPA 엔티티 · Repository는 쓰지 않고 SQL만 씁니다.
- 다른 모듈 테이블을 JOIN하는 SQL은 `adapter/out/persistence/query/` 아래에 모아서, 테이블을 바꿀 때 `grep`으로 찾을 수 있게 합니다.
- 공개 범위 · 차단 · soft delete 조건은 JOIN 쿼리에서도 빠뜨리지 않습니다 (통합 테스트 필수, 7.2).

### 6.2 모듈 의존 그래프 (허용 목록)

화살표는 "왼쪽이 오른쪽의 공개 API를 사용"입니다. 이벤트 구독도 의존으로 칩니다.

```
notification ──▶ user, social, feed, collection, creator, conversation, plan   (이벤트 구독, 수신 설정 확인)
moderation   ──▶ user, feed, collection, conversation, admin
settlement   ──▶ creator, collection, admin
collection   ──▶ user, social, place, savedplace, media, creator, payment
creator      ──▶ user, admin
plan         ──▶ user, social, place, savedplace, route
placeimport  ──▶ place, savedplace
conversation ──▶ user, social, media, place, feed
feed         ──▶ user, social, place, media
savedplace   ──▶ user, social, place
route        ──▶ place
place        ──▶ media
social       ──▶ user
auth         ──▶ user, terms
user · terms · media · payment · admin   (다른 모듈에 의존하지 않음)
common       ◀── 모든 모듈 (common은 어떤 모듈에도 의존하지 않음)
```

이 목록은 `ArchitectureTest`에 그대로 코드로 둡니다 (7.1). **새 화살표가 필요하면 이 문서와 테스트를 같은 PR에서 고치고**, 리뷰에서 순환 여부를 확인합니다.

### 6.3 동기 호출과 이벤트 선택 기준

| 조건 | 선택 |
| --- | --- |
| 결과가 지금 필요함 (검증, 금액, 권한) | **동기 in-port 호출** |
| 두 변경이 함께 성공·실패해야 함 (결제 승인 + 구매 생성) | **동기 호출, 같은 트랜잭션** (상위 모듈 방향이면 in-port, 하위 → 상위면 SPI) |
| 하위 모듈이 상위 모듈의 데이터를 같은 트랜잭션에서 정리해야 함 | **동기 이벤트** (`@EventListener`) — 예: 저장 장소 삭제 → 컬렉션 구성 장소 제거 |
| 일이 끝난 뒤 알리기만 함, 실패해도 원래 작업은 유효 | **커밋 후 이벤트** (`@TransactionalEventListener(AFTER_COMMIT)`) — 알림 |
| 오래 걸리는 작업을 응답과 분리 | **커밋 후 이벤트 + `@Async`** + DB 상태 기반 복구 스케줄러 — 링크 추출, 이미지 처리 |
| 외부 시스템에 정확히 한 번 전달해야 함 | 지금은 해당 없음. 생기면 Transactional Outbox 도입 |

### 6.4 이벤트 목록

| 이벤트 | 소유(발행) | 발행 시점 | 구독 | 방식 |
| --- | --- | --- | --- | --- |
| `UserWithdrawnEvent` | user | 익명화 저장 직전 (30일 뒤 예약 작업) | auth (소셜 연결 · 토큰 삭제), feed (게시물 삭제), 회원 개인 데이터를 가진 모듈 (삭제) | **동기, 같은 트랜잭션** (하나라도 실패하면 익명화도 되돌아가 다음 날 다시 처리) |
| `UserStatusChangedEvent` | user | 정지 · 영구 정지 · 해제 저장 후 | auth (기간·영구 정지 시 전체 Refresh Token 폐기), notification | 커밋 후 |
| `UserFollowedEvent` | social | 팔로우 저장 후 | notification | 커밋 후 |
| `FeedLikedEvent`, `CommentCreatedEvent`, `CommentLikedEvent` | feed | 저장 후 | notification | 커밋 후 |
| `SavedPlaceDeletingEvent` | savedplace | **삭제 직전** (FK 때문에 구성 장소를 먼저 지움) | collection | 동기, 같은 트랜잭션 |
| `SavedPlaceCollectionsChangedEvent` | savedplace | 저장 · 수정 직후 (요청에 컬렉션 필드가 있을 때) | collection | 동기, 같은 트랜잭션. 컬렉션 소유자 검증 실패면 저장도 롤백 (8.1 #13) |
| `CollectionPurchasedEvent`, `PurchaseRefundedEvent` | collection | 구매 생성 · 환불 반영 후 | notification | 커밋 후 |
| `CreatorApprovedEvent`, `CreatorRejectedEvent` | creator | 상태 변경 후 | notification | 커밋 후 |
| `CreatorSuspendedEvent` | creator | 박탈 후 | collection (정책 미정), notification | 커밋 후 |
| `MessageSentEvent` | conversation | 저장 후 | notification (DM 알림 여부 미정) | 커밋 후 |
| `ImportJobRequestedEvent` | placeimport | 작업 저장 후 | placeimport 자신 | 커밋 후 + `@Async` |
| `MediaUploadedEvent` | media | 업로드 확인 후 | media 자신 | 커밋 후 + `@Async` |

- **이벤트 클래스는 발행 모듈의 `domain/event`에 있고, 발행 모듈이 소유합니다.** 필드를 바꾸면 구독 모듈이 영향을 받으므로 공개 API와 같은 리뷰 대상입니다.
- 이벤트에는 **id와 화면에 필요한 최소 값**만 넣습니다 (엔티티 · JPA 객체 금지).
- 발행은 application service가 상태를 저장한 직후 `ApplicationEventPublisher`로 합니다. domain은 이벤트 객체만 만들고 Spring을 모릅니다.
- 커밋 후 이벤트는 서버가 중단되면 유실될 수 있습니다. 그래서 **유실되면 안 되는 일은 이벤트에 맡기지 않거나**(결제), **DB 상태로 복구 가능하게** 만듭니다(링크 추출 · 이미지 처리).

### 6.5 트랜잭션 경계

| 규칙 | 내용 |
| --- | --- |
| 기본 | **유스케이스 하나 = 트랜잭션 하나.** `@Transactional`은 application service에만. 조회는 `readOnly = true` |
| 다른 모듈 in-port 호출 | 호출한 쪽 트랜잭션에 참여 (`REQUIRED`). 호출받는 쪽은 `REQUIRES_NEW`를 쓰지 않음 |
| 관리자 작업 기록 | 업무 변경과 같은 트랜잭션 (기록 없는 변경이 생기지 않게) |
| 외부 호출 | PG · LLM · 웹 수집 · 길찾기 · 메일은 **트랜잭션 밖**. 앞뒤 상태를 나눠 저장 (5.2.4, 5.3.3) |
| 배치 | 처리 단위(크리에이터 한 명, 작업 한 단계)마다 트랜잭션 |
| 잠금 | 돈과 권한이 걸린 행(`payments`, `collection_purchases`)은 `SELECT … FOR UPDATE`. 상태 전이는 조건부 UPDATE(`WHERE status = ?`)로 이중 처리 방지. ERD에 version 컬럼이 없어 낙관적 잠금은 쓰지 않음 |
| 순서 변경 | `UNIQUE(plan_id, position)` 등은 일괄 재배치 중간에 위반됩니다. 제약을 `DEFERRABLE INITIALLY DEFERRED`로 만들거나 두 단계 갱신 (8.4 #4) |

### 6.6 순환 의존 방지

1. 의존 허용 목록(6.2)을 ArchUnit 테스트로 강제합니다. 목록에 없는 의존은 빌드 실패입니다.
2. 하위 모듈이 상위 모듈을 불러야 하면, 순서대로 검토합니다.
   - 정말 필요한가? (조회라면 상위 모듈이 하위 모듈 데이터를 가져가면 되는 경우가 많음)
   - 알리기만 하면 되는가? → 이벤트
   - 같은 트랜잭션 결과가 필요한가? → 하위 모듈이 SPI(out-port)를 정의하고 상위 모듈이 구현
3. 그래도 양쪽이 서로 필요하면 **모듈 경계가 잘못된 것**입니다. 두 모듈을 합치거나 세 번째 모듈로 나눕니다 (`settlement`를 `creator`에서 뺀 이유).

---

## 7. ArchUnit 검증 규칙 · 테스트 책임

### 7.1 ArchUnit 규칙

| # | 규칙 | 검사 내용 |
| --- | --- | --- |
| 1 | domain 순수성 | `..domain..`은 `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `com.fasterxml.jackson..`, `..application..`, `..adapter..`에 의존하지 않음 |
| 2 | domain 모듈 격리 | 모듈 A의 domain은 자기 모듈 domain과 `common.domain`, `java..`만 사용 |
| 3 | application 격리 | `..application..`은 `..adapter..`, `jakarta.persistence..`, `org.springframework.web..`, `org.springframework.data..`에 의존하지 않음 (`@Transactional`, `ApplicationEventPublisher`는 허용) |
| 4 | 입력 어댑터는 in-port만 | `..adapter.in..`은 `..application.service..`, `..application.port.out..`, `..adapter.out..`에 의존하지 않음 |
| 5 | 포트는 인터페이스 | `..application.port.in..`, `..application.port.out..`의 클래스는 interface |
| 6 | 서비스는 in-port 구현 | `@Service` 이면서 `..application.service..`에 있는 클래스는 같은 모듈 `port.in` 인터페이스를 하나 이상 구현 (단계 클래스 `*Steps`는 예외 목록) |
| 7 | 어댑터는 out-port 구현 | `..adapter.out..`의 `*Adapter`는 같은 모듈 `port.out` 인터페이스를 하나 이상 구현 |
| 8 | JPA 위치 | `@Entity`는 `..adapter.out.persistence.entity..`에만, Spring Data `Repository`는 `..adapter.out.persistence.repository..`에만 |
| 9 | Repository 접근 | Spring Data `Repository`는 **같은 모듈**의 `..adapter.out.persistence..`에서만 접근 |
| 10 | 웹 위치 | `@RestController`는 `..adapter.in.web..`에만, `@MessageMapping`은 `..adapter.in.websocket..`에만, `@Scheduled`는 `..adapter.in.scheduler..`에만 |
| 11 | 모듈 공개 API | 다른 모듈 접근은 `application.port.in..`, `application.dto..`, `domain.event..`만. SPI 예외는 테스트에 명시 |
| 12 | 의존 허용 목록 | 6.2 그래프에 없는 모듈 의존 금지 |
| 13 | 순환 금지 | `slices().matching("com.pickong.(*)..").should().beFreeOfCycles()` |
| 14 | common 독립 | `com.pickong.common..`은 다른 모듈 패키지에 의존하지 않음 |

```java
@AnalyzeClasses(packages = "com.pickong", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain은_인프라를_모른다 = noClasses()
            .that().resideInAPackage("com.pickong.*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                    "com.fasterxml.jackson..", "..application..", "..adapter..");

    @ArchTest
    static final ArchRule 입력_어댑터는_in_port만_호출한다 = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application.service..", "..application.port.out..", "..adapter.out..");

    @ArchTest
    static final ArchRule 모듈_간_순환이_없다 = slices()
            .matching("com.pickong.(*)..").should().beFreeOfCycles();

    /** 6.2와 같은 내용. 바꾸면 문서도 함께 수정 */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            entry("feed", Set.of("user", "social", "place", "media")),
            entry("collection", Set.of("user", "social", "place", "savedplace", "media", "creator", "payment"))
            /* … */);

    @ArchTest
    static final ArchRule 허용된_모듈에만_공개_API로_의존한다 = classes()
            .that().resideInAPackage("com.pickong..")
            .should(ModuleRules.dependOnlyOnAllowedModulesThroughPublicApi(ALLOWED,
                    /* SPI 예외 */ Set.of("com.pickong.payment.application.port.out.PaymentPurposeHandler")));
}
```

`ModuleRules`는 의존 대상의 모듈 이름(패키지 두 번째 단어)과 하위 패키지를 확인하는 커스텀 `ArchCondition` 하나입니다 (`test/.../support/arch/`).

### 7.2 테스트 책임

| 종류 | 대상 | 도구 · 방식 | 검증하는 것 | 검증하지 않는 것 |
| --- | --- | --- | --- | --- |
| **Domain 단위** | `domain/model`, `vo`, `policy` | JUnit만 (Spring 없음, ms 단위) | 상태 전이, 가격 · 수수료 · 정산 계산, 환불 판정, 순서 최적화, 충돌 검증, 일치도 | DB, 트랜잭션 |
| **Application** | `application/service` | **Fake Port**(메모리 구현, `test/support/fake/`) + 고정 `Clock` | 유스케이스 흐름, 권한 확인, 어떤 포트를 어떤 순서로 부르는지, 실패 시 상태 | SQL, JSON 형식 |
| **Persistence 통합** | `*PersistenceAdapter`, `*QueryAdapter` | Testcontainers `postgis/postgis:16-*` + Flyway 전체 적용 | 매핑, 조회 쿼리, 공개 범위 · 차단 · soft delete 필터, UNIQUE · CHECK 제약, 잠금 · 조건부 UPDATE, PostGIS 범위 조회 | 업무 규칙 |
| **API 통합** | HTTP · STOMP 전체 경로 | `@SpringBootTest` + MockMvc / STOMP 클라이언트 + Testcontainers, 외부 Port는 테스트 어댑터 | 인증 쿠키, 오류 응답 형식, 경로 · 요청 검증, 권한별 응답, 결제 · 환불 · 정산 같은 핵심 시나리오 | 모든 분기 (그건 Application 테스트) |
| **아키텍처** | 전체 | ArchUnit | 7.1 | |

- Fake를 Mock보다 먼저 씁니다. Mock은 호출 순서에 묶여서 리팩터링할 때 테스트가 함께 깨집니다. 외부 API 오류처럼 만들기 어려운 상황만 Mock을 씁니다.
- 외부 어댑터(PG, LLM, 지도)는 계약 테스트를 따로 두고 CI 기본 실행에서는 제외합니다 (실제 키 필요).

---

## 8. ERD / API 불일치와 미결정 사항

> **스키마는 바꾸지 않았습니다.** 아래 "제안"은 결정을 돕기 위한 선택지이며, 확정되면 ERD · 명세서 · 이 문서를 함께 고칩니다.
> 영향 모듈을 구현하기 전에 정해야 하는 항목은 ★로 표시했습니다.

### 8.1 API 구현에 필요한 데이터가 없음

| # | 내용 | 영향 | 제안 |
| --- | --- | --- | --- |
| 1 ★ | **일정 공개 범위:** `PUT /plans/{planId}`(공개범위), `GET /users/{username}/plans`(공개 일정), 상세 "공개 일정 포함"이 있는데 `plans`에 `visibility`가 없음. 프론트는 "일정은 본인만"으로 구현됨 (8.5 #1) | plan | (a) `plans.visibility` 추가 (b) 일정 공개 API를 보류로 변경 |
| 2 ★ | **일정 체류 시간:** 일정 장소 수정 API에 "체류시간", 충돌 검증에 체류 시간이 필요한데 `plan_items`에 컬럼 없음 | plan | (a) `plan_items.stay_minutes` 추가 (b) 카테고리별 기본값(설정) (c) 체류 시간 없이 이동 시간만 검증. 지금 설계는 (c) + `STAY_TIME_UNKNOWN` 경고 |
| 3 ★ | **지도 공개 범위:** `GET /users/{username}/saved-places`(다른 사용자의 공개 지도)는 미완료(구현 대상)인데, 공개 여부를 정하는 지도 공개 범위 API는 보류이고 컬럼도 없음 | savedplace, user | (a) 보류 해제 + `user_settings.map_visibility` 추가 (b) 공개 컬렉션에 담긴 장소만 공개 (c) 이 API도 보류 |
| 4 | **저장 장소 개인 사진:** 프론트 · mock은 `/me/places/{placeId}/photos`를 씀. ERD에 `saved_place_photos`(2026-11-20)가 추가됐지만 마이그레이션은 없음 | savedplace | ERD대로 `saved_place_photos(saved_place_id, media_id, position)`. UNIQUE(saved_place_id, position), UNIQUE(media_id). 파일 업로드(media) 구현 후 |
| 5 ★ | **AI 추출 결과(후보 목록) 저장 위치 없음.** 재시도 횟수 · 다음 재시도 시각도 없음 | placeimport | (a) `import_candidates` 테이블 (b) `import_jobs.result JSONB`. 재시도는 `retry_count` 컬럼 |
| 6 | 컬렉션 장소 수정 API(`PUT …/items/{itemId}`)가 있는데 `collection_places`에 바꿀 컬럼이 순서뿐 | collection | 장소별 크리에이터 설명이 필요하면 `note` 컬럼, 아니면 API 삭제 |
| 7 | 저장 장소 개인 태그: 이전에는 보류였지만 ERD에 `saved_place_tags`(2026-11-20)가 추가됐고, 프론트 · mock은 `/me/places/{placeId}/tags`를 씀 | savedplace | 메모는 `PATCH /saved-places`의 `memo`. 태그는 ERD대로 구현(달거나 지울 때 `place_tags.count`, `user_tag_stats.score` 갱신). 태그 테이블(`tags` 등) 마이그레이션 필요 |
| 8 ★ | **관리자 로그인 · 토큰 API 없음.** `refresh_tokens.user_id`는 회원만 가리킴 | admin, auth | 관리자 로그인 API + 관리자 세션 방식 결정 (별도 토큰 테이블 또는 짧은 세션) |
| 9 | **비밀번호 재설정 토큰** 저장 위치 없음 | auth | 테이블(해시 · 만료 · 사용 시각) 또는 만료 있는 캐시 |
| 10 | DM 재전송 중복 방지 키 없음 (재연결 시 같은 메시지 두 번 저장) | conversation | `messages.client_message_id` + UNIQUE(sender_id, client_message_id) |
| 11 | DM 안 읽은 대화 수(프론트 하단바 배지, mock `GET /conversations/unread-count`)가 명세에 없음 | conversation | 명세 추가 (`last_read_message_id`로 계산 가능) |
| 12 | **저장 출처 컬렉션:** ERD의 `saved_places.source_collection_id`(FK → collections)는 `collections` 마이그레이션이 없어 만들지 못했음(V19로 테이블이 생겨 이제 추가 가능). 출처 문구(`source_label`) · 원본 URL(`source_url`) · 게시물(`source_feed_id`)은 ERD에 있어 해결됨 | savedplace, collection | savedplace 저장 API 작업에서 컬럼 · FK 추가. 그 전에는 `COLLECTION` 출처를 종류 · 문구만 저장 |
| 13 | **저장하면서 컬렉션에 담기:** 프론트 · mock은 `POST /saved-places`의 `collectionIds`와 `PATCH /saved-places`의 `add/set/removeCollectionIds`로 컬렉션 구성까지 한 번에 바꿈. `collection_places`는 collection 모듈 소유라 savedplace가 직접 쓰거나 collection의 UseCase를 부르면 `savedplace → collection` 화살표가 생겨, 이미 있는 `collection → savedplace`와 서로 의존하게 됨(6.2 비순환 원칙). DB 관계(`collection_places`가 `collections` · `saved_places`를 FK로 잇는 다대다)에는 문제가 없음 | savedplace, collection | **결정: 이벤트 방식 (2026-11-26).** savedplace가 저장 · 수정 직후 같은 트랜잭션에서 `SavedPlaceCollectionsChangedEvent`를 발행하고, collection의 리스너가 `collection_places`를 쓰며 컬렉션 소유자를 검증함(실패하면 함께 롤백). 삭제의 `SavedPlaceDeletingEvent`와 같은 방식이고, 코드 의존은 `collection → savedplace` 한 방향뿐. `collections` · `collection_places`는 V19로 생겼으므로 리스너를 구현할 수 있음. 구현 전에는 필드를 받기만 하고 무시 |
| 12 | 개인 크리에이터 · 사업자 구분 없음 → 원천징수 3.3% "개인만" 적용 불가 | settlement, creator | `creators.business_type` 또는 정산 계좌에 사업자 정보 |
| 13 | 일정 날씨: `plans.region`은 자유 텍스트라 좌표가 없음 | plan | 일정 장소들의 좌표(첫 장소 또는 중심점) 사용 — 스키마 변경 없이 가능 |

### 8.2 ERD에는 있지만 API에서 쓰지 않는 데이터

| # | 데이터 | 확인할 것 |
| --- | --- | --- |
| 1 | `place_business_hours`, `place_hours_exceptions`, `places.hours_source`, `hours_verified_at` | 누가 채우나? (지도 API에 영업시간이 없으면 관리자 입력 · 크롤링 · 사용자 제보 중 무엇인지) |
| 2 | `place_external_refs.last_synced_at` | 외부 장소 정보 동기화 작업 필요 여부 |
| 3 | `creator_grades` | 등급 관리 API 없음 → Flyway 초기 데이터로 넣을지 |
| 4 | `creator_payout_accounts.verified_at` | 계좌 인증(1원 인증) API 없음 → 없으면 모든 정산이 HELD |
| 5 | `creator_settlements` PAID · `paid_at` | 지급 완료 처리 API 없음 |
| 6 | `user_sanctions` WARNING · 사유 | 회원이 자기 제재 내역을 보는 API 없음 |
| 7 | `import_extraction_runs.corrected_count`, `saved_count` | 일괄 저장은 `POST /saved-places`(`source.type = LINK_IMPORT`)로 하고 `saved_places`에는 원문 URL(`source_url`)만 남음. 작업 id가 없어 어느 작업의 결과인지 알 수 없음 |
| 8 | `import_extraction_runs.job_id` NULL 허용 | 작업 없이 실행하는 경우(평가용 등)가 있는지 |
| 9 | `users.name`, `phone_number` | 어느 API에서 받는지, 필요한 개인정보인지 |
| 10 | `refresh_tokens.user_agent`, `ip_address` | 로그인 기기 목록 API를 만들지 (없으면 감사용) |
| 11 | `admins.last_login_at` | 관리자 로그인 API와 함께 (8.1 #8) |
| 12 | `notifications.detail`, `link` | 알림 종류별 문구 · 링크 생성 규칙 |

### 8.3 도메인 책임이 불분명한 데이터

| # | 데이터 | 현재 결정 | 남은 문제 |
| --- | --- | --- | --- |
| 1 | `users.password_hash`, `email` | 저장은 user, 해시 생성 · 검증은 auth | — |
| 2 | `collection_places.owner_id`, `place_id` (저장 장소에서 복사한 값, 복합 FK) | collection 소유 | 저장 장소의 장소가 바뀌는 경우가 있는지 |
| 3 | `/users/{username}/visits`의 기준 | feed (게시물에 연결한 장소, 프론트 부록 A.1 기준) | `saved_places`의 VISITED 기준이면 savedplace로 이동 |
| 4 | `payments.collection_id` | payment 소유, 값은 SPI가 채움 | 결제 목적이 늘면 목적별 컬럼이 늘어남 → 범용 `target_id` 검토 |
| 5 | `reports.target_type/target_id`, `notifications.target_*` | 다형 참조 (FK 없음) | 대상 삭제 시 처리 (신고는 스냅샷이 있어 유지) |
| 6 | `places.visibility`, `owner_id` (직접 추가 장소) | place | CUSTOM 장소를 누가 볼 수 있는지 규칙 |

### 8.4 트랜잭션 · 제약 조건 누락

| # | 내용 | 제안 |
| --- | --- | --- |
| 1 ★ | **구매 스냅샷의 완전성:** `collection_version_places`에는 장소 id · 순서 · 이름만 있음. 주소 · 좌표 · 카테고리 · 사진 · (있다면) 크리에이터 설명은 현재 `places` 값을 보여 주게 되어 구매 후 바뀔 수 있음. 버전에 표지 · 핀 스타일도 없음 | 구매자에게 보장할 범위를 정한 뒤, 필요한 값을 `collection_version_places`에 복사 (예: 주소 · 좌표 · 카테고리) |
| 2 | 구매자는 구매 버전과 최신 버전 중 무엇을 보나? ("이 버전을 언제든 볼 수 있음" vs "가격 변경 시 기존 구매자 추가 금액 없음") | 제안: 상세는 최신 버전, 스냅샷 API는 구매 버전 |
| 3 | ERD 내보내기 SQL이 MySQL 문법(백틱), FK가 10개만 있음, PK에 IDENTITY 없음 | Flyway 초기 스크립트를 PostgreSQL 문법으로 작성하고 모든 FK · 주석에 적힌 UNIQUE · CHECK · 부분 UNIQUE 인덱스를 포함 |
| 4 | `UNIQUE(plan_id, position)`, `UNIQUE(user_id, position)`(지도 카테고리), `UNIQUE(feed_id, position)` — 일괄 재배치 중간에 위반 | `DEFERRABLE INITIALLY DEFERRED` 또는 임시 음수 위치로 두 단계 갱신 |
| 5 | 시각 타입 혼용: `feeds`, `comments`, `feed_*`, `comment_likes`만 `TIMESTAMP` | `TIMESTAMPTZ`로 통일 |
| 6 | `collection_purchases.fee_amount`가 NULL 허용 | 구매 시 항상 계산하므로 NOT NULL |
| 7 | `payments.status` · `refunds` 외 상태 값 목록, 결제 준비 만료 시각 없음 | 상태 값 확정 (APPROVING 포함 여부), READY 만료 처리 |
| 8 | `collection_places` → `saved_places` FK 삭제 정책 | 앱에서 동기 이벤트로 먼저 정리 (6.4). DB는 `RESTRICT` 유지 |
| 9 | `conversations.last_message_id` ↔ `messages.conversation_id` 상호 참조 | 메시지 저장 후 대화방 갱신 순서 고정 |
| 10 | `follows`, `user_blocks` 자기 자신 금지가 주석에만 있음 | CHECK 제약. `follows`는 V20에서 `ck_follows_not_self`로 반영함. `user_blocks`는 테이블 마이그레이션이 아직 없음 |

### 8.5 서로 충돌하는 규칙

| # | 충돌 | 결정 필요 |
| --- | --- | --- |
| 1 ★ | 일정 공개(API 명세) ↔ 일정은 본인만, 공유는 문구로(프론트 9.3 · 부록 A.1) | 일정을 공개하는가 |
| 2 ★ | 예약 시간 · 이동 시간 · 체류 시간 충돌 검증(이번 요청, PlanRoute 미완료) ↔ 일정 자동 점검 보류(common 부록 A.7) · 체류 시간 제거(프론트 9.3). 또 고정 시각이 `plan_items.fixed_time`과 예약 `start_time` 두 곳에 있음 | 검증 범위, 고정 시각의 기준 컬럼 |
| 3 | prepare ~ confirm 사이 가격 변경 | 준비 금액으로 승인할지, 거절하고 다시 준비할지 |
| 4 ★ | "열람 전에만 환불" ↔ 어떤 API가 열람을 기록하는지 불명확 (상세 GET? 스냅샷 GET?). GET이 환불 자격을 없애므로 미리 가져오기 · 봇이 문제 | 열람 기록 API를 하나로 정하고 프론트는 그 API를 사용자 행동에서만 호출 |
| 5 | 크리에이터 박탈(SUSPENDED) · 유료 컬렉션을 PRIVATE으로 바꿀 때(가격은 PUBLIC만) 판매 · 기존 구매자 열람 | 판매 중지, 기존 구매자는 계속 열람 등 |
| 6 | 환불 기한 · 수수료 · 원천징수 원 단위 처리(내림/반올림), 정산 기간 기준(구매일 vs 환불 불가가 된 날) | 숫자와 기준 |
| 7 | 정지(SUSPENDED) 회원이 할 수 있는 일 (로그인 · 조회 · DM) | 정책 |
| 8 | `refunds.reason_code`에 ADMIN · PAYMENT_ERROR가 있음 ↔ "열람 전에만 환불", "정산 후 환불 불가" | 관리자 환불은 예외인지, 정산 후 환불은 어떻게 차감하는지 |
| 9 | 프론트 테스트 결제 `POST /collections/:id/purchase` ↔ 명세 `/payments/prepare` · `/confirm` | 명세 기준으로 프론트 수정 |

### 8.6 외부 공개 UUID와 내부 BIGINT 기준

| 기준 | 적용 |
| --- | --- |
| **공유 링크로 열리는 리소스는 `public_id`(UUID)** | feeds(`{feedId}`), places, collections, plans |
| **본인 · 참여자 · 관리자만 접근하는 리소스는 BIGINT `id`** + 서비스에서 소유자 확인 | 댓글, 저장 장소, 일정 장소, 구매, 결제, 정산, 알림, 대화방, 메시지, 추출 작업, 미디어, 신고, 제재, 장소 제보(`/admin/place-submissions/{id}`는 DB id 문자열. 응답 `placeId`는 장소 `public_id`) |
| **사용자는 `username`** | `/users/{username}`. 관리자 API만 `{userId}` |
| 변환 위치 | 컨트롤러는 경로 값을 그대로 Command에 담고, **service가 `PlaceQueryUseCase.resolveId(publicId)` 등으로 내부 id로 바꿈**. domain과 다른 모듈 호출은 내부 id(Long)로 통일 |

확인할 것:
- `username`은 바꿀 수 있나? (`PUT /me`) 바꿀 수 있으면 공유된 프로필 링크가 깨집니다.
- 응답에 다른 회원의 내부 `id`를 노출할지 (DM 참여자 등). 제안: 공개 응답은 `username`, 내부 id는 관리자 API에서만.
- 일정이 본인 전용이면(8.5 #1) `plans.public_id`는 지금은 필요 없지만 그대로 둡니다.

### 8.7 그 밖의 미결정 사항

| # | 내용 |
| --- | --- |
| 1 ★ | **Spring Boot 버전:** 요청서는 3.4, `build.gradle`은 4.1.2. 3.4는 오픈소스 지원이 끝난 버전이라 새 프로젝트로 낮추는 것은 권하지 않습니다. 정하면 0.1과 `build.gradle`을 맞춥니다 |
| 2 | enum 값 목록이 없는 컬럼: `media_files.purpose/status`, `payments.status/purpose/provider`, `import_jobs.status/source_type/platform`, `places.source_type/category`, `reports.reason`(접수는 `ReportReason` 여섯 값. 컬럼은 문자열이라 기존 다른 값은 관리자 조회에 남음. 상태·대상·처리 액션은 확정), `notifications.type/target_type`, `messages.attachment_type`, `plan_item_reservations.type`, 이동 수단 `mode` |
| 3 | 길찾기 · 날씨 · LLM · PG · 메일 제공자 |
| 4 | 보류 테이블(tags 등)을 Flyway 초기 스크립트에 포함할지 (제안: 보류를 풀 때 마이그레이션 추가) |
| 5 | R2 파일 접근 방식: 공개 버킷이면 비공개 게시물 · 유료 컬렉션 사진이 URL만으로 열림 → 비공개 버킷 + 서명 URL(GET) 여부 |
| 6 | DM으로 공유한 FOLLOWERS 게시물을 팔로워가 아닌 상대가 볼 수 있는지 (제안: 열람 시점에 공개 범위 다시 확인) |
| 7 | 피드 상세 조회는 공개 범위를 검사하지 않습니다. 신고 접수는 다른 회원에게 `PUBLIC`만 허용하고, 상세가 열려 있다는 이유로 `FOLLOWERS`·`PRIVATE` 신고를 허용하지 않습니다. `follows` 마이그레이션이 생기고 피드 목록이 팔로워 글을 실제로 보여 주면 신고 조건을 맞춥니다 |

---

## 9. 기능을 추가할 때의 패키지 배치 규칙

### 9.1 어디에 둘까 — 판단 순서

1. **이 기능이 쓰는(바꾸는) 테이블의 소유 모듈은?** (2.1) → 그 모듈에 둡니다. URL은 상관없습니다.
2. 새 테이블이면, **그 데이터의 규칙을 누가 가장 잘 아는가**로 모듈을 정합니다. 애매하면 팀에서 정하고 2.1 표에 추가합니다.
3. 여러 모듈의 데이터를 함께 바꿔야 하면, **주된 변경이 일어나는 모듈**에 유스케이스를 두고 나머지는 공개 포트로 부릅니다 (6.3).
4. 그 의존이 6.2 그래프에 없으면, 코드보다 **문서 · ArchUnit 허용 목록을 먼저** 고치는 PR을 올립니다.

### 9.2 모듈 안에서 어디에 둘까

| 만들 것 | 위치 | 판단 기준 |
| --- | --- | --- |
| 새 API | `adapter/in/web/<리소스>Controller` + `request/` · `response/` | 관리자 API면 `adapter/in/web/admin/` |
| 새 동작 | 기존 `<리소스>CommandUseCase`에 메서드 추가 | 새 인터페이스는 4.3 분리 조건일 때만 |
| 새 조회 화면 | `<리소스>QueryUseCase` + `QueryPort` 메서드 | 여러 테이블 JOIN이면 domain을 거치지 않음 |
| 업무 규칙 | 모델 하나의 규칙 → `domain/model` 메서드, 여러 모델 · 외부 값이 필요 → `domain/policy` | service에 `if`로 규칙을 쓰지 않음 |
| 검증 있는 값 | `domain/vo` | 두 곳 이상에서 같은 검증을 할 때 |
| 새 테이블 | 소유 모듈 `persistence/entity` + Flyway `V{n}__*.sql` | 4.2 단계에 따라 domain 모델 여부 결정 |
| 외부 API | `application/port/out/<역할>Port` + `adapter/out/external/<대상>/` | 외부 응답 DTO는 어댑터 밖으로 내보내지 않음 |
| 다른 모듈 기능 사용 | service에서 그 모듈의 in-port 호출 | out-port를 새로 만들지 않음 (SPI 예외만) |
| 다른 모듈에 알리기 | `domain/event`에 이벤트 + service에서 발행 | 구독은 받는 모듈의 `adapter/in/event` |
| 예약 작업 | `adapter/in/scheduler` → in-port 호출 | 로직은 service에 |
| 오류 | 모듈 `<Module>ErrorCode`에 추가 | `common`에 추가하지 않음 |

### 9.3 만들지 않을 것

- 메서드 하나짜리 UseCase 인터페이스를 동작마다 만들지 않습니다 (4.3).
- 규칙 없는 테이블에 domain 모델 · Mapper를 만들지 않습니다 (4.2 2단계).
- 서비스끼리 서로 호출하지 않습니다. 공통 로직은 domain으로 내리고, 다른 모듈이면 in-port를 씁니다.
- 구현이 하나뿐인 외부 연동에 팩토리 · 전략 계층을 얹지 않습니다. 두 번째 구현이 생길 때 선택 로직을 추가합니다 (`RoutingWebContentFetchAdapter`처럼).
- `common`에 업무 규칙을 넣지 않습니다 (4.5).

### 9.4 PR 체크리스트

- [ ] 새 클래스가 소유 모듈 · 올바른 계층에 있다 (9.1, 9.2)
- [ ] domain에 Spring · JPA import가 없다
- [ ] 다른 모듈은 in-port · dto · event로만 쓴다. 새 모듈 의존이면 6.2와 `ArchitectureTest`를 함께 고쳤다
- [ ] 권한 확인(소유자 · 공개 범위 · 차단)이 service에 있다
- [ ] 외부 API 호출이 트랜잭션 밖에 있다
- [ ] 스키마 변경은 새 Flyway 파일이고, ERD도 갱신했다
- [ ] domain 단위 테스트 + (쿼리를 바꿨다면) Persistence 통합 테스트가 있다

### 9.5 이름 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 모듈 패키지 | 소문자 한 단어 | `savedplace`, `placeimport` |
| 모델 · VO · 정책 | 명사 / 정책은 판단 내용 + `Policy` · 계산은 `Calculator` | `Plan`, `CollectionPrice`, `RefundWindowPolicy`, `SettlementCalculator` |
| 이벤트 | 과거형(완료) 또는 진행형(직전) + `Event`, record | `FeedLikedEvent`, `SavedPlaceDeletingEvent` |
| in-port | `<대상>CommandUseCase` · `<대상>QueryUseCase` · `<흐름>UseCase` | `PlanQueryUseCase`, `LoginUseCase` |
| dto | `<동사><대상>Command` · `<대상>Query` · `<대상>Result` | `CreatePlanCommand`, `PlanDetailResult` |
| out-port | `<대상>PersistencePort` · `<모듈>QueryPort` · `<역할>Port` | `PlanPersistencePort`, `WeatherPort` |
| service | in-port 이름의 `UseCase` → `Service`. 트랜잭션 단계 클래스는 `<흐름>Steps` | `PlanCommandService`, `PaymentApprovalSteps` |
| controller | `<리소스>Controller` (URL 용어), 관리자는 `Admin<리소스>Controller` | `FeedController`, `AdminCreatorController` |
| web dto | `<동작><리소스>Request` · `<리소스>Response` | `CreateFeedRequest`, `FeedResponse` |
| JPA | `<테이블 단수>JpaEntity`, `<테이블 단수>JpaRepository` | `PlanItemJpaEntity` |
| adapter | `<기술 · 대상><역할>Adapter` | `KakaoLocalSearchAdapter`, `R2StorageAdapter` |
| 리스너 · 스케줄러 | `<이벤트 또는 작업>Listener` · `<작업>Scheduler` | `ImportJobRequestedListener`, `MonthlySettlementScheduler` |
| 테스트 | `<대상>Test`, 통합은 `<대상>IntegrationTest` | `VisitOrderOptimizerTest` |

---

## 부록 A. 보류 기능을 다시 넣을 때의 위치

[common_blueprint.md](common_blueprint.md) 7장의 보류 기능은 지금 구조에 넣지 않았습니다. 보류를 풀면 아래 위치에 추가하며, 기존 모듈 구조는 바뀌지 않습니다.

| 기능 | 위치 | 의존 |
| --- | --- | --- |
| 태그 (`tags`, `feed_tags`, `place_tags`, `user_tag_stats`) | 새 모듈 `tag`. 게시물 작성·수정 시 `feed`가 `TagUseCase`를 호출해 태그 연결과 통계(`place_tags.count`, `user_tag_stats.score`) 갱신을 같은 트랜잭션에서 요청 | `feed → tag` 한 방향. `tag`가 `feed` 이벤트를 구독하면 순환이 생기므로 이벤트는 쓰지 않음 |
| 디바이스 · 푸시 (`user_devices`) | `notification`에 `DevicePersistencePort` + `PushSenderPort` (`adapter/out/external/fcm/`) | 변화 없음 (notification은 최상위) |
| 지도 공개 범위 | `user`의 사용자 설정 (컬럼 추가 필요, 8.1 #3) | `savedplace`가 `UserSettingsUseCase` 조회 |
| 알림 전체 읽음 | `NotificationUseCase.markAllRead` | 변화 없음 |
| AI 일정 추천 | `plan`에 `PlanRecommendationUseCase` + `PlanRecommenderPort` (`adapter/out/external/llm/`) | `plan → savedplace, place` (기존) |
| 일정 자동 점검 (영업시간) | `plan/domain/policy/ScheduleConflictChecker`에 영업시간 경고 추가, 값은 `PlaceQueryUseCase`가 `OpeningHoursPolicy`로 제공 | 기존 |
