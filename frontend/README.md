# 찜콩 프론트엔드

> 당신의 마침표. 누군가의 "따옴표"가 됩니다

설계 규칙은 저장소 루트의 [`frontend_blueprint.md`](../frontend_blueprint.md)와 [`common_blueprint.md`](../common_blueprint.md)를 따릅니다.

## 실행

```bash
npm install
npm run dev        # mock API(localhost:4000) + Next.js(localhost:3000)를 함께 실행
```

브라우저에서 http://localhost:3000 을 열면 홈 피드(`/feed`)로 이동합니다. 모바일 폭(390px)에 맞춘 화면이라 개발자 도구의 기기 모드로 보는 것을 권장합니다.

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | mock 서버 + Next 개발 서버 |
| `npm run mock` | mock 서버만 실행 (Node 24의 TypeScript 실행 기능 사용) |
| `npm run mock:stores` | 상가 정보 CSV로 mock 주변 장소(`mocks/fixtures/storePlaces.json`)를 다시 만듦. 실행 중인 mock은 다시 시작해야 반영됨 |
| `npm run test` | 순수 로직 단위 테스트 (Vitest) |
| `npm run lint` | ESLint + 계층 import 규칙 |
| `npm run typecheck` | TypeScript 검사 |
| `npm run build` | 프로덕션 빌드 |

## Docker 빌드 및 실행

저장소 루트에서 실행합니다. Dockerfile은 Node.js 24 기반이며, 로컬용 `development`와 배포용 `builder` → `runner` 단계로 구성됩니다.
`builder`에서 `npm ci`, 단위 테스트, 프로덕션 빌드를 수행하고, `runner`에서 standalone 서버를 일반 사용자로 실행합니다.

```bash
# 1차: 테스트 및 빌드만 확인
docker build --target builder -t pickong-frontend:build ./frontend

# 2차: 실행 이미지 생성 및 서버 실행 (앞 단계의 빌드 캐시 사용)
docker build -t pickong-frontend ./frontend
docker run --rm -p 3000:3000 pickong-frontend
```

화면은 http://localhost:3000 에서 확인합니다. mock 서버는 이미지에서 실행하지 않습니다.
API 요청을 사용하려면 백엔드가 같은 Docker 네트워크에서 접근 가능해야 합니다.

### Compose로 로컬 개발

저장소 루트에서 실행하면 `compose.yml`과 `compose.override.yml`이 자동으로 병합됩니다.

```bash
docker compose up --build -d
docker compose logs -f frontend mock
docker compose down
```

- 프론트엔드: http://localhost:3000 (Next.js 개발 서버, 소스 변경 자동 반영)
- mock API: http://localhost:4000/api/me (파일 변경 시 재시작, 메모리 데이터 초기화)
- 프론트엔드 `/api/*` 요청은 Docker 네트워크의 `http://mock:4000`으로 전달됩니다.
- 시작할 때 `npm ci`로 lockfile에 맞게 의존성을 설치합니다. 의존성을 변경하면 `docker compose restart frontend`로 반영합니다.
- 컨테이너의 `node_modules`와 `.next`는 전용 볼륨에 보관해 호스트 파일과 분리합니다.

`compose.yml`은 프론트엔드 빌드 설정과 내부 포트만 정의하는 베이스입니다.
로컬 포트 공개, 소스 마운트, 개발 서버 명령, mock 서비스는 `compose.override.yml`에 있습니다.
`compose.prod.yml`은 운영 설정을 추후 작성할 **빈 파일**입니다.
운영 구성을 작성한 뒤에는 `docker compose -f compose.yml -f compose.prod.yml up --build -d`처럼 파일을 명시해 로컬 override를 제외합니다.

배포용 `runner`의 `API_BASE_URL`은 rewrites에 사용되어 **빌드 시 고정**됩니다 (기본 `http://backend:8080`).
운영 설정 시 `build.args.API_BASE_URL`에 백엔드 서비스 주소를 지정하고 주소가 바뀌면 다시 빌드해야 합니다.
로컬 개발 서버는 `environment.API_BASE_URL`을 사용합니다.
Docker Desktop에서 호스트의 mock 서버에 연결하려면 빌드할 때
`--build-arg API_BASE_URL=http://host.docker.internal:4000`을 지정합니다.
로컬 `.env*` 파일은 이미지에 복사하지 않습니다.

## Mock API → 실제 백엔드

- 화면은 항상 `/api/v1/*`로 요청하고, `next.config.ts`의 rewrites가 `API_BASE_URL`(기본 `http://localhost:4000`, mock)로 전달합니다.
- **백엔드에 있는 API만 백엔드로 보내기:** `.env.local`에 `BACKEND_URL=http://localhost:8080`을 넣고 `npm run dev`로 실행합니다 (`.env.local.example` 참고).
  - `next.config.ts`의 `BACKEND_PATHS`에 있는 회원 인증·약관 API와 `/api/v1/admin/users/**` · `/api/v1/admin/feeds/**` · `/api/v1/admin/reports/**`가 백엔드로 가고, 나머지는 mock으로 갑니다.
  - 백엔드에 새 API가 생기면 `BACKEND_PATHS`에 경로를 추가합니다.
- 재동의 화면(`/terms/consent`)을 mock으로 보려면 `MOCK_RECONSENT=PRIVACY,MARKETING npm run dev`처럼 실행합니다. 지정한 약관이 재동의가 필요한 다음 버전(예: 1.1 → 1.2)으로 바뀐 것으로 나옵니다. 서버의 403 차단은 mock이 흉내 내지 않으므로, 차단 · 이동까지 보려면 백엔드에 `requires_reconsent = true`인 새 버전 행을 넣고 확인합니다.
  - 백엔드 실행 방법은 [`../backend/README.md`](../backend/README.md)를 봅니다. 백엔드의 `FRONTEND_BASE_URL`은 `http://localhost:3000`(기본값)이어야 로그인 후 이 화면으로 돌아옵니다.
- **전부 백엔드로 보내기:** `API_BASE_URL=http://localhost:8080`을 넣고 `npm run next`로 Next만 실행합니다. 백엔드에 없는 화면은 404가 납니다.
- 쓰기 요청(POST · PUT · PATCH · DELETE)은 `http.ts`가 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 자동으로 붙입니다. mock은 CSRF를 확인하지 않습니다.
- mock 데이터는 `mocks/fixtures/`, 응답 규칙은 `mocks/handlers/`에 있습니다. 메모리에만 저장되므로 mock 서버를 다시 시작하면 초기 상태로 돌아갑니다.
- 지도의 주변 장소에는 예시 장소 외에 실제 상가 정보(홍대 · 명동 · 잠실 · 강남 · 성수의 카페 · 음식점 · 소품샵류 · 갤러리 약 2만 4천 곳)가 들어 있습니다. 소상공인시장진흥공단 상가(상권)정보를 `npm run mock:stores`로 변환한 것이고, 업종 기준은 저장소 루트의 `place_category_mapping.md`를 따릅니다. 장소가 많으면 mock도 백엔드처럼 묶음(`clusters`)으로 응답합니다. 성수동 상가 장소에는 화면 확인용 예시 영업시간(24시간 표기, 자정을 넘기지 않음)이 붙어 있습니다.
- 응답 형태가 바뀌면 `src/entities/<엔티티>/api/mapper.ts`에서 흡수합니다. 화면 코드는 도메인 모델만 사용합니다.

### 관리자 사용자·콘텐츠·신고 관리 연동

- `BACKEND_URL`을 설정한 뒤 Next 서버를 다시 시작하면 `/admin/users`, `/admin/contents`의 게시물 탭, `/admin/reports`가 실제 관리자 API를 사용합니다. `/admin/contents` 화면 경로는 그대로이며 게시물 API는 `/api/v1/admin/feeds`입니다.
- 관리자 로그인 전체 구현은 별도 작업입니다. 일반 회원의 소셜 로그인 쿠키로는 403이며, 관리자 인증이 없으면 401을 화면에 표시합니다. 이 401로 회원 Refresh Token을 사용하거나 회원 로그인 화면으로 이동하지 않습니다. 운영용 임시 관리자나 인증 우회는 제공하지 않습니다.
- `BACKEND_URL` 없이 mock으로 화면 흐름을 확인할 수 있습니다. mock은 관리자 인증을 검증하지 않으므로 실제 관리자 로그인 검증을 대신하지 않습니다.
- 사용자 응답의 `isCreator: null`은 확인 불가로 표시하며, 탈퇴 상태도 그대로 표시합니다. 유효한 제재마다 `sanctionId`를 사용해 개별 해제합니다. 경고 알림은 발송하지 않습니다.
- 게시물의 `link: null`이면 상세 링크를 만들지 않습니다. 컬렉션 탭은 연동 준비 안내를 표시하며 API를 호출하지 않습니다. 컬렉션 신고는 기각만 가능합니다.
- 처리 성공 및 상태 충돌 시 관리자 목록·신고·게시물·댓글 캐시를 다시 조회합니다. 미구현 대시보드 요약·크리에이터·장소 제보 API 등은 부분 연동 환경에서 계속 mock을 사용합니다. 회원용 피드 API의 전환은 이번 관리자 연동 범위에 포함하지 않습니다.

### 확인해 볼 만한 예시 상황

| 상황 | 방법 |
| --- | --- |
| 사진을 넘기면 장소가 바뀜 · 장소 없는 사진 | 홈 첫 게시물(지민) 사진 1~5 |
| 전체 장소 저장 시 중복 제거 · 새로 저장/기존 연결 구분 | 첫 게시물 → 장소 찜콩 → 전체 장소 |
| 링크 접근 실패 → 텍스트 입력 | `/import`에서 주소에 `private` 포함 |
| 위치 불명확 후보는 자동 저장하지 않음 | `/import`에서 아무 공개 링크 → ‘노을 전망대’ |
| 일정 순서 · 고정 시간 · 계획 완료 | `/plans/plan-1`(계획 중)에서 장소를 끌어 옮기고, 장소를 눌러 고정 시간 적기 → ‘계획 완료’ |
| 방문 기록 · 다른 곳 추가 | 계획 완료 후 ‘방문 기록하기’ → 전체 선택 · ‘다른 곳 추가’ |

## 폴더

```
mocks/          개발용 mock API 서버 (앱 번들에 포함되지 않음)
src/
  app/          라우트 + 페이지 전용 코드 (_components, _lib, _api)
    (main)/(tabs)/   하단 내비게이션이 있는 화면: 홈 · 내 지도 · 컬렉션 · DM · 프로필(일정 탭 포함)
    (main)/(sub)/    뒤로가기 중심의 세부 작업 화면
  widgets/      post-card
  features/     save-place · like-post · bookmark-post · follow-user · place-search · create-custom-place
  entities/     user · place · post · collection · plan (모델 · DTO · mapper · API · queries · 표시 UI)
  shared/       ui · map · api · lib · config
```

## 아직 연결하지 않은 것

- 로그인 · 회원가입 · `proxy.ts` 접근 제어 (mock은 항상 ‘서연’ 계정으로 응답)
- 카카오 지도 SDK: 지금은 `shared/map`의 개념 지도 일러스트. 화면은 `MapView` props만 쓰므로 `shared/map` 안에서 교체합니다. 내 지도의 ‘주변 장소’는 mock의 예시 장소와 상가 정보 장소입니다.
- 사진 업로드 API: 기기에서 고른 사진은 브라우저 임시 URL이라 새로고침하면 사라집니다.
- 일정의 장소 사이 이동 시간은 직선거리로 어림한 참고값입니다. 영업시간은 예시 데이터입니다.
