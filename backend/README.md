# 찜콩 백엔드

Spring Boot 4.1 · Java 21 · Gradle · PostgreSQL 16(PostGIS) · Flyway.

## 실행

```bash
docker compose up -d db   # 저장소 루트에서. 로컬 PostgreSQL(PostGIS) 실행 (compose.override.yml의 db 서비스)
./gradlew bootRun         # http://localhost:8080 — 시작할 때 Flyway 마이그레이션이 자동 적용됩니다
./gradlew test            # Docker Desktop이 켜져 있어야 합니다 (아래 '테스트')
./gradlew build           # 컴파일 + 테스트 + jar 생성 (build/libs/)
```

### DB 접속 정보

`application.yml`은 환경변수를 읽고, 없으면 로컬 db 서비스 값을 씁니다. 운영에서는 반드시 환경변수로 넣습니다.

| 환경변수 | 로컬 기본값 |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/pickong` |
| `DB_USERNAME` | `pickong` |
| `DB_PASSWORD` | `pickong` |
| `JWT_SECRET` | **없음 (필수).** 없거나 32바이트보다 짧으면 앱이 시작하지 않습니다 |
| `COOKIE_SECURE` | `true`. 로컬(http://localhost)은 `.env`에서 `false` |
| `COOKIE_DOMAIN` | 비움 (지금 도메인에만 쿠키 저장) |
| `FRONTEND_BASE_URL` | `http://localhost:3000`. 소셜 로그인 콜백 주소 · 로그인 후 이동할 화면의 기준 |
| `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET` | 없으면 앱은 뜨지만 카카오 로그인만 실패. **값이 빈 줄(`KAKAO_CLIENT_ID=`)은 "없음"이 아니라 빈 값이라 앱이 시작하지 않으니** 키가 없으면 줄을 주석으로 둡니다 |
| `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET` | 위와 같음 (없으면 Google 로그인만 실패, 빈 줄은 시작 실패) |

### 처음 실행할 때: `backend/.env`

비밀값은 git에 올라가지 않는 `backend/.env`에 넣으면 Spring이 직접 읽습니다. (운영은 환경변수로 넣습니다)

```bash
cp .env.example .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env   # 또는 .env를 열어 직접 채움
```

### 스키마 (Flyway)

- 스키마는 `src/main/resources/db/migration`의 Flyway 파일로만 만듭니다. JPA 스키마 자동 생성 · 검증(`ddl-auto`)은 쓰지 않습니다.
- `V1__create_auth_tables.sql`: 인증에 필요한 테이블(`users`, `user_social_accounts`, `refresh_tokens`, `terms`, `user_terms_agreements`)만 있습니다. 다른 테이블은 각 모듈 담당자가 **새 파일(`V2__…`, `V3__…`)** 로 추가합니다.
- 이미 적용된 마이그레이션 파일은 고치지 않습니다. 바꿀 것이 있으면 새 버전 파일을 추가합니다.

### 테스트

- DB가 필요 없는 테스트(서비스 · 컨트롤러 단위 테스트)는 Mockito로 DB 없이 돕니다.
- DB가 필요한 테스트는 `@Import(TestcontainersConfiguration.class)`를 붙이면 실제 PostgreSQL(PostGIS 16) 컨테이너에서 Flyway를 적용한 뒤 실행됩니다.
- **Docker Desktop이 꺼져 있으면 DB 테스트는 실패합니다.** (건너뛰지 않음 — CI가 없어서, 건너뛰면 아무도 DB 테스트를 돌리지 않게 되기 때문)

Gradle을 따로 설치하지 않아도 `./gradlew`가 필요한 버전(9.8.2)을 내려받습니다. Windows에서는 `gradlew.bat`을 씁니다. JDK 21이 필요합니다.

의존성은 `build.gradle`의 `dependencies`에 추가합니다. 버전은 Spring Boot가 관리하므로 Boot가 관리하지 않는 라이브러리만 버전을 적습니다.

| 주소 | 내용 |
| --- | --- |
| `GET /api/v1/actuator/health` | 상태 확인 (servlet path가 `/api/v1`이라 actuator도 그 아래에 있습니다) |
| `/api/v1/swagger-ui.html` | API 문서. POST · PATCH · DELETE를 보낼 때 CSRF 헤더를 자동으로 붙입니다 |
| `/api/v1/v3/api-docs` | OpenAPI 스펙 JSON. 프론트는 이 스펙으로 DTO 타입을 생성할 수 있습니다 |

## 인증 · 보안

- **인증:** 쿠키 `pickong_at`의 Access Token(JWT, HS256, 15분)을 매 요청 검증합니다. 세션은 쓰지 않습니다.
- **로그인 유지:** 쿠키 `pickong_rt`의 Refresh Token(랜덤 문자열, 14일, DB에는 SHA-256 해시만)으로 재발급합니다.
  - `POST /api/v1/auth/refresh`: 204 + 새 쿠키 두 개. 쓴 Refresh Token은 폐기되고 만료는 다시 14일로 연장됩니다.
  - 이미 폐기된 토큰이 다시 오면(탈취 의심) 그 로그인 세션 전체를 폐기하고 401 `REFRESH_TOKEN_REUSED`. 정지 · 탈퇴 회원은 401 `ACCOUNT_UNAVAILABLE`. 실패하면 쿠키도 지웁니다.
  - `POST /api/v1/auth/logout`: 이 기기의 세션만 폐기하고 쿠키 삭제, 항상 204.
  - 쿠키: 모두 HttpOnly · SameSite=Lax · Secure(`COOKIE_SECURE`). Access는 `Path=/`, Refresh는 `Path=/api/v1/auth`.
  - 정리: 만료된 지 7일이 지난 토큰 행은 매일 04:00(한국 시간) `ExpiredTokenCleanupScheduler`가 지웁니다. 기간 · 시각은 `pickong.auth.refresh-token.cleanup-retention` · `cleanup-cron`.
- **기본은 로그인 필요**이고, 공개 경로는 `auth/adapter/in/security/SecurityConfig`의 `PUBLIC_PATHS`에만 둡니다.
  - 경로는 `/api/v1`을 빼고 적습니다. DispatcherServlet이 `/api/v1`에 매핑되어 있으면 Spring Boot가 보안 경로의 기준을 `/api/v1`로 잡기 때문입니다 (`"/auth/**"` = `/api/v1/auth/**`).
  - `/feeds/**`는 #38(피드 인증 연동) 전까지 임시로 공개입니다.
- **컨트롤러에서 로그인 회원 받기:**
  ```java
  @PostMapping("/feeds")
  FeedResponse create(@CurrentUser LoginUser user, ...)                       // 비로그인이면 401
  @GetMapping("/feeds/{feedId}")
  FeedResponse get(@CurrentUser(required = false) LoginUser viewer, ...)      // 비로그인이면 null
  ```
- **권한 없음:** `throw new BusinessException(CommonErrorCode.FORBIDDEN)` → 403
- **CSRF:** 상태를 바꾸는 요청(POST · PUT · PATCH · DELETE)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 합니다. 없으면 403 `INVALID_CSRF_TOKEN`. Swagger UI는 자동으로 붙이고, Postman은 GET 응답의 `XSRF-TOKEN` 쿠키 값을 헤더에 넣습니다.
- **오류 응답:** `{ "status", "code", "message" }`. 오류 코드는 모듈마다 `ErrorCode`를 구현한 enum으로 둡니다 (`feed/domain/FeedErrorCode`, 공통은 `common/error/CommonErrorCode`).

## 소셜 로그인 (카카오 · Google)

- 시작: `GET /api/v1/auth/oauth2/authorization/{kakao|google}` (프론트의 버튼이 이 주소로 페이지 이동)
- 콜백: `/api/v1/auth/oauth2/callback/{kakao|google}` (제공자가 돌려보냄, 프론트 도메인으로 받아 Next가 백엔드로 전달)
- 결과:
  - 기존 회원 → Access · Refresh 쿠키 발급 → `{FRONTEND_BASE_URL}/feed`
  - 신규 회원 → 임시 가입 쿠키 `pickong_signup`(30분) → `{FRONTEND_BASE_URL}/signup`
  - 정지 · 탈퇴 → `/login?error=ACCOUNT_UNAVAILABLE`, 이메일이 다른 계정에 있음 → `/login?error=EMAIL_ALREADY_REGISTERED&provider=kakao|google`
  - 취소 → `/login?error=OAUTH_CANCELLED`, state 불일치 · 제공자 오류 → `/login?error=OAUTH_FAILED`
- OAuth state는 서버 세션 대신 서명된 쿠키 `pickong_oauth`(10분)에 보관하고, 콜백의 state와 비교합니다(로그인 CSRF 방어).
- **OAuth2 경로(시작 · 콜백)는 보안 설정에서 `/api/v1`을 포함한 전체 경로로 적습니다.** 인가 규칙(`PUBLIC_PATHS`)과 달리 OAuth2 필터에는 Spring Boot의 basePath가 붙지 않습니다.

### 최초 가입 온보딩 (임시 가입 쿠키 `pickong_signup`로 동작)

| API | 결과 |
| --- | --- |
| `GET /api/v1/auth/signup` | 200 소셜 계정 정보 `{provider, name, profileImageUrl, email}` · 401 `SIGNUP_SESSION_EXPIRED` · 409 `ALREADY_REGISTERED`(이미 로그인) |
| `GET /api/v1/auth/username-availability?username=` | 200 `{available}` (대소문자 무시, 예약어는 false) · 400 `INVALID_USERNAME` |
| `POST /api/v1/auth/signup` `{username, name, useSocialProfileImage, agreedTerms}` | 200 `{username}` + 세션 쿠키, 가입 쿠키 삭제 · 400 `INVALID_USERNAME` · `INVALID_NAME` · `REQUIRED_TERMS_NOT_AGREED` · `UNKNOWN_TERMS` · 409 `USERNAME_TAKEN` · `EMAIL_ALREADY_REGISTERED` · `SOCIAL_ACCOUNT_ALREADY_LINKED` · `ALREADY_REGISTERED` · 401 `SIGNUP_SESSION_EXPIRED` |
| `DELETE /api/v1/auth/signup` | 204, 가입 쿠키 삭제 |

- 회원 생성 · 소셜 계정 연결 · 약관 동의가 한 트랜잭션이라, 하나라도 실패하면 아무것도 남지 않습니다.
- 약관 데이터는 Flyway `V2__seed_terms.sql`에 있습니다. 약관을 바꿀 때는 새 버전 행을 넣는 새 마이그레이션을 추가합니다.

### 로그인 수단 관리 (로그인 필요, `{provider}`는 `kakao` · `google`)

| API | 결과 |
| --- | --- |
| `GET /api/v1/me/social-accounts` | 200 `[{provider, email, connectedAt, canUnlink}]` 연결 순서대로. 가입할 때 쓴 수단만 `canUnlink=false` |
| `POST /api/v1/me/social-accounts/{provider}` | 200 `{authorizationUrl}` (연결 토큰 5분) · 409 `PROVIDER_ALREADY_LINKED` · 400 `INVALID_INPUT` |
| `DELETE /api/v1/me/social-accounts/{provider}` | 204 · 404 `SOCIAL_ACCOUNT_NOT_LINKED` · 409 `SIGNUP_METHOD_CANNOT_BE_UNLINKED` |

- 연결 흐름: `POST`로 받은 `authorizationUrl`(`/api/v1/auth/oauth2/authorization/{provider}?link=…`)로 이동 → 제공자 계정 선택 · 동의 → 같은 콜백 → `{FRONTEND_BASE_URL}/settings/login-methods?linked=kakao`
- 연결 실패는 `/settings/login-methods?error=…`: `LINK_EXPIRED`(연결 토큰 만료 · 위조, 로그인 쿠키 없음 · 다른 회원) · `SOCIAL_ACCOUNT_ALREADY_LINKED` · `PROVIDER_ALREADY_LINKED` · `EMAIL_ALREADY_REGISTERED` · `ACCOUNT_UNAVAILABLE` · `OAUTH_CANCELLED` · `OAUTH_FAILED`
- 해제는 찜콩에서만 합니다. 카카오 · Google 쪽 앱 연결은 사용자가 각 계정 설정에서 끊을 수 있습니다.

### 내 정보 · 프로필 수정 (로그인 필요)

| API | 결과 |
| --- | --- |
| `GET /api/v1/me` | 200 `{id, username, nickname, name, profileImageUrl, bio, region, postCount, followerCount, followingCount, isFollowing, isMe, isCreator, usernameChangeableAt}` |
| `PATCH /api/v1/me` `{username?, name?, bio?, region?}` | 200 바뀐 내 정보 · 400 `INVALID_USERNAME` · `INVALID_NAME` · `INVALID_BIO` · `INVALID_REGION` · 409 `USERNAME_TAKEN` · `USERNAME_CHANGE_TOO_SOON` |

- 보낸 항목만 바꾸고, `null`을 보내면 지웁니다(아이디 제외). 한 항목이라도 틀리면 아무것도 저장하지 않습니다.
- 아이디는 14일에 한 번만 바꿀 수 있습니다(가입 후 첫 변경은 바로). 다음 변경 가능 시각은 `usernameChangeableAt`(지금 가능하면 null).
- `id`는 `users.public_id`, `nickname`은 이름 ?? 아이디입니다.

### 약관 (전문은 로그인 없이, 동의 관리는 로그인 필요)

| API | 결과 |
| --- | --- |
| `GET /api/v1/terms/{type}` (`service` · `privacy` · `marketing`, 대소문자 무관) | 200 `{type, title, required, version, effectiveDate, sections: [{heading, body}]}` 지금 시행 중인 버전 · 404 `TERMS_NOT_FOUND` |
| `GET /api/v1/me/agreements` | 200 `[{type, title, required, version, agreed, agreedAt}]` SERVICE · PRIVACY · MARKETING 순서. 동의했으면 동의한 버전과 그 버전의 제목 |
| `PATCH /api/v1/me/agreements` `{type, agreed}` | 200 바뀐 목록 · 400 `REQUIRED_TERMS_CANNOT_BE_REVOKED`(필수 약관) · `UNKNOWN_TERMS` · `INVALID_INPUT`(agreed가 없거나 true · false가 아님) |
| `GET /api/v1/me/agreements/pending` | 200 `[{type, title, required, version}]` 지금 동의 받을 약관(재동의). 없으면 `[]` |
| `POST /api/v1/me/agreements` `{agreedTerms}` | 200 바뀐 동의 목록 · 400 `REQUIRED_TERMS_NOT_AGREED`(동의 받을 필수 약관이 빠짐) · `UNKNOWN_TERMS` |
| (재동의 전 그 밖의 로그인 API) | 403 `TERMS_RECONSENT_REQUIRED`. `/terms/**` · `/me/agreements/**` · `GET /me` · `POST /me/withdrawal`(탈퇴 신청) · `/auth/**`는 열림 |

- 약관 본문은 `terms_sections`(V8)에 버전마다 저장합니다. 문구를 바꿀 때는 `terms`에 새 버전 행과 조항을 넣는 새 마이그레이션을 추가하고, 기존 행은 고치지 않습니다(회원이 동의한 버전의 내용을 남기기 위해). 버전 행과 조항은 꼭 같은 마이그레이션에 넣습니다.
- 새 버전마다 `requires_reconsent`(V10)를 정합니다. `true`면 이전 버전에 한 동의를 인정하지 않아, 필수 약관은 동의할 때까지 서비스를 쓸 수 없고 선택 약관은 예전에 동의했던 회원에게 다시 묻습니다. 문구 정리처럼 다시 물을 필요가 없으면 `false`(기본값). 새 필수 약관 종류를 추가할 때는 `true`로 넣어야 기존 회원에게 묻습니다.
- 철회는 `user_terms_agreements.revoked_at`만 채우고 기록을 지우지 않습니다. 이미 같은 상태면 아무것도 바꾸지 않습니다.
- 철회하지 않은 동의는 부분 유니크 인덱스(V8)로 버전마다 하나만 남아, 동의 요청이 동시에 와도 중복되지 않습니다.

### 회원 탈퇴 (30일 유예 후 익명화)

| API | 결과 |
| --- | --- |
| `POST /api/v1/me/withdrawal` `{reason, detail}` | 204, 모든 기기 세션 폐기 · 쿠키 삭제 · 400 `INVALID_WITHDRAWAL_REASON` · 409 `WITHDRAWAL_NOT_ALLOWED` |
| `GET /api/v1/auth/withdrawal` (탈퇴 취소 쿠키) | 200 `{username, requestedAt, anonymizeAt}` · 401 `RESTORE_SESSION_EXPIRED` · 409 `WITHDRAWAL_NOT_CANCELLABLE` |
| `POST /api/v1/auth/withdrawal/cancel` (탈퇴 취소 쿠키) | 200 `{username}` + 세션 쿠키, 탈퇴 취소 쿠키 삭제 · 오류는 위와 같음 |
| `DELETE /api/v1/auth/withdrawal` | 204, 탈퇴 취소 쿠키만 삭제 (탈퇴는 그대로) |

- 탈퇴 중인 회원이 소셜 로그인하면 로그인 세션 대신 탈퇴 취소 쿠키(`pickong_restore`, 10분)를 받고 `/account/restore`로 갑니다. 30일이 지났으면 `/login?error=ACCOUNT_UNAVAILABLE`.
- 매일 04:30(한국 시간)에 신청 후 30일이 지난 회원을 익명화합니다(`pickong.user.withdrawal.anonymize-cron`). 개인정보를 지우고 아이디를 `deleted_{id}`로 바꾸며, 소셜 연결 · 토큰도 같은 트랜잭션에서 지웁니다. 실패한 회원은 다음 날 다시 처리됩니다.
- 탈퇴 사유는 `withdrawal_reasons`(V11)에 회원 id 없이 날짜만 붙여 저장합니다.

### 개발자 콘솔에서 직접 설정할 것

**카카오** (developers.kakao.com > 내 애플리케이션)
1. 앱 키 > REST API 키 → `KAKAO_CLIENT_ID`
2. 보안 > Client Secret 코드 생성 · 활성화 → `KAKAO_CLIENT_SECRET`
3. 카카오 로그인 > 활성화, Redirect URI 등록: `http://localhost:3000/api/v1/auth/oauth2/callback/kakao` (운영: `https://프론트도메인/api/v1/auth/oauth2/callback/kakao`)
4. 앱 설정 > 비즈 앱 전환 (개인 개발자도 가능). 비즈 앱이 아니면 이메일 동의항목을 켤 수 없습니다.
5. 카카오 로그인 > 동의항목: 닉네임 · 프로필 사진(필수 또는 선택), 카카오계정(이메일) **필수 동의**
   - 백엔드가 이 세 항목(`profile_nickname`, `profile_image`, `account_email`)을 요청하므로 하나라도 "사용 안 함"이면 카카오 로그인 뒤 `KOE205` 오류가 납니다.
6. 플랫폼 > Web 사이트 도메인: `http://localhost:3000` (운영 도메인 추가)

**Google** (console.cloud.google.com > API 및 서비스)
1. OAuth 동의 화면 설정 (범위: `openid`, `email`, `profile`), 테스트 단계에서는 테스트 사용자 추가
2. 사용자 인증 정보 > OAuth 2.0 클라이언트 ID(웹 애플리케이션) 생성 → `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET`
3. 승인된 리디렉션 URI: `http://localhost:3000/api/v1/auth/oauth2/callback/google` (운영 주소 추가)

**로컬에서 실제 로그인 확인**: 키를 넣은 뒤 `frontend/.env.local`에 `BACKEND_URL=http://localhost:8080`을 넣고 백엔드와 프론트(`npm run dev`)를 띄워 `http://localhost:3000/login`에서 시작합니다. (Redirect URI가 3000이라 8080으로 직접 열면 콜백이 프론트로 돌아옵니다)

## 프론트엔드와 맞출 것

- **API 계약**: 프론트는 지금 `frontend/mocks/`의 mock 서버로 동작합니다. 엔드포인트와 응답 형태는 `frontend/mocks/handlers/`, 샘플 데이터는 `frontend/mocks/fixtures/`가 기준입니다.
- **경로**: 모든 API는 `/api/`로 시작합니다. 프론트는 같은 도메인의 `/api/*`로 요청하고 Next가 백엔드로 전달하므로 CORS 설정은 필요 없습니다.
- **오류 응답**: `{ "status": 404, "code": "FEED_NOT_FOUND", "message": "사용자에게 보여줄 문장" }` 형태로 보냅니다. 프론트는 HTTP 상태와 `code`로 분기하고 `message`를 그대로 표시합니다.
- **권한**: 비공개·팔로워 공개 리소스의 접근 판단은 백엔드가 합니다. 볼 수 없으면 403 또는 404를 보냅니다.

프론트를 이 서버에 연결하려면 `frontend/.env.local`에 `API_BASE_URL=http://localhost:8080`을 넣고 `npm run next`로 Next만 실행합니다.

## 추가할 것

- 로그인 · 세션 (카카오 · Google 소셜 로그인, JWT httpOnly 쿠키)
- 카카오 장소 검색 프록시 (`GET /api/places/search`, REST 키는 백엔드에만 둡니다)
- 이동 시간 계산, AI 링크 추출 작업
