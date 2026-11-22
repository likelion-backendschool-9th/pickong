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
| `/swagger-ui.html` | API 문서 (`/api/**` 경로만 표시) |
| `/v3/api-docs` | OpenAPI 스펙 JSON. 프론트는 이 스펙으로 DTO 타입을 생성할 수 있습니다 |

## 프론트엔드와 맞출 것

- **API 계약**: 프론트는 지금 `frontend/mocks/`의 mock 서버로 동작합니다. 엔드포인트와 응답 형태는 `frontend/mocks/handlers/`, 샘플 데이터는 `frontend/mocks/fixtures/`가 기준입니다.
- **경로**: 모든 API는 `/api/`로 시작합니다. 프론트는 같은 도메인의 `/api/*`로 요청하고 Next가 백엔드로 전달하므로 CORS 설정은 필요 없습니다.
- **오류 응답**: `{ "code": "NOT_FOUND", "message": "사용자에게 보여줄 문장" }` 형태로 보냅니다. 프론트는 HTTP 상태와 `code`로 분기하고 `message`를 그대로 표시합니다.
- **권한**: 비공개·팔로워 공개 리소스의 접근 판단은 백엔드가 합니다. 볼 수 없으면 403 또는 404를 보냅니다.

프론트를 이 서버에 연결하려면 `frontend/.env.local`에 `API_BASE_URL=http://localhost:8080`을 넣고 `npm run next`로 Next만 실행합니다.

## 추가할 것

- 로그인 · 세션 (카카오 · Google 소셜 로그인, JWT httpOnly 쿠키)
- 카카오 장소 검색 프록시 (`GET /api/places/search`, REST 키는 백엔드에만 둡니다)
- 이동 시간 계산, AI 링크 추출 작업
