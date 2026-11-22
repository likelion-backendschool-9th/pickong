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
