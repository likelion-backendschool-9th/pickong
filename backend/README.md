# 찜콩 백엔드

Spring Boot 4.1 · Java 21 · Gradle. 지금은 실행 가능한 기본 틀만 있고, API는 아직 없습니다.

## 실행

```bash
./gradlew bootRun   # http://localhost:8080
./gradlew test
./gradlew build     # 컴파일 + 테스트 + jar 생성 (build/libs/)
```

Gradle을 따로 설치하지 않아도 `./gradlew`가 필요한 버전(9.8.2)을 내려받습니다. Windows에서는 `gradlew.bat`을 씁니다. JDK 21이 필요합니다.

의존성은 `build.gradle`의 `dependencies`에 추가합니다. 버전은 Spring Boot가 관리하므로 Boot가 관리하지 않는 라이브러리만 버전을 적습니다.

| 주소 | 내용 |
| --- | --- |
| `GET /actuator/health` | 상태 확인 |
| `/swagger-ui.html` | API 문서 (`/api/**` 경로만 표시) |
| `/v3/api-docs` | OpenAPI 스펙 JSON. 프론트는 이 스펙으로 DTO 타입을 생성할 수 있습니다 |

## 프론트엔드와 맞출 것

- **API 계약**: 프론트는 지금 `frontend/mocks/`의 mock 서버로 동작합니다. 엔드포인트와 응답 형태는 `frontend/mocks/handlers/`, 샘플 데이터는 `frontend/mocks/fixtures/`가 기준입니다.
- **경로**: 모든 API는 `/api/`로 시작합니다. 프론트는 같은 도메인의 `/api/*`로 요청하고 Next가 백엔드로 전달하므로 CORS 설정은 필요 없습니다.
- **오류 응답**: `{ "code": "NOT_FOUND", "message": "사용자에게 보여줄 문장" }` 형태로 보냅니다. 프론트는 HTTP 상태와 `code`로 분기하고 `message`를 그대로 표시합니다.
- **권한**: 비공개·팔로워 공개 리소스의 접근 판단은 백엔드가 합니다. 볼 수 없으면 403 또는 404를 보냅니다.

프론트를 이 서버에 연결하려면 `frontend/.env.local`에 `API_BASE_URL=http://localhost:8080`을 넣고 `npm run next`로 Next만 실행합니다.

## 추가할 것

- PostgreSQL + PostGIS (장소 좌표·반경 검색), 마이그레이션 도구
- 로그인 · 세션 (httpOnly 쿠키)
- 카카오 장소 검색 프록시 (`GET /api/places/search`, REST 키는 백엔드에만 둡니다)
- 이동 시간 계산, AI 링크 추출 작업
