# 찜콩 프론트엔드

> 당신의 마침표. 누군가의 "따옴표"가 됩니다

설계 규칙은 저장소 루트의 [`BLUEPRINT.md`](../BLUEPRINT.md)를 따릅니다.

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
| `npm run test` | 순수 로직 단위 테스트 (Vitest) |
| `npm run lint` | ESLint + 계층 import 규칙 |
| `npm run typecheck` | TypeScript 검사 |
| `npm run build` | 프로덕션 빌드 |

## Docker 빌드 및 실행

저장소 루트에서 실행합니다. Dockerfile은 Node.js 24 기반의 두 단계로 구성됩니다.
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

추후 루트 `compose.yml`에는 다음과 같이 연결할 수 있습니다 (현재는 설정 예시).

```yaml
services:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      target: runner
      args:
        API_BASE_URL: http://backend:8080
    ports:
      - "3000:3000"
```

`backend`는 추후 Compose에 정의할 백엔드 서비스 이름이며, `8080`은 컨테이너 내부 포트입니다.
`API_BASE_URL`은 rewrites에 사용되어 **빌드 시 고정**됩니다. 주소를 바꾸면 `build.args`를 수정하고
`docker compose up --build`로 다시 빌드해야 합니다. 실행 시 `environment`만 바꿔서는 반영되지 않습니다.
Docker Desktop에서 호스트의 mock 서버에 연결하려면 빌드할 때
`--build-arg API_BASE_URL=http://host.docker.internal:4000`을 지정합니다.
로컬 `.env*` 파일은 이미지에 복사하지 않습니다.

## Mock API → 실제 백엔드

- 화면은 항상 `/api/*`로 요청하고, `next.config.ts`의 rewrites가 `API_BASE_URL`(기본 `http://localhost:4000`)로 전달합니다.
- 백엔드([`../backend`](../backend/README.md))에 연결하려면 `.env.local`에 `API_BASE_URL=http://localhost:8080`을 넣고 `npm run next`로 Next만 실행합니다 (`.env.local.example` 참고).
- mock 데이터는 `mocks/fixtures/`, 응답 규칙은 `mocks/handlers/`에 있습니다. 메모리에만 저장되므로 mock 서버를 다시 시작하면 초기 상태로 돌아갑니다.
- 응답 형태가 바뀌면 `src/entities/<엔티티>/api/mapper.ts`에서 흡수합니다. 화면 코드는 도메인 모델만 사용합니다.

### 확인해 볼 만한 예시 상황

| 상황 | 방법 |
| --- | --- |
| 사진을 넘기면 장소가 바뀜 · 장소 없는 사진 | 홈 첫 게시물(지민) 사진 1~5 |
| 전체 장소 저장 시 중복 제거 · 새로 저장/기존 연결 구분 | 첫 게시물 → 장소 찜콩 → 전체 장소 |
| 링크 접근 실패 → 텍스트 입력 | `/import`에서 주소에 `private` 포함 |
| 위치 불명확 후보는 자동 저장하지 않음 | `/import`에서 아무 공개 링크 → ‘노을 전망대’ |
| 영업 종료 · 예약 지연 경고와 해결 | `/plans/plan-1`에서 ‘전시 공간 결’을 영화관 B 뒤로 옮기기, 또는 카페 온유 체류 시간 늘리기 |

## 폴더

```
mocks/          개발용 mock API 서버 (앱 번들에 포함되지 않음)
src/
  app/          라우트 + 페이지 전용 코드 (_components, _lib, _api)
    (main)/(tabs)/   하단 내비게이션이 있는 화면: 홈 · 내 지도 · 컬렉션 · 일정 · 프로필
    (main)/(sub)/    뒤로가기 중심의 세부 작업 화면
  widgets/      post-card
  features/     save-place · like-post · bookmark-post · follow-user · place-search · create-custom-place
  entities/     user · place · post · collection · plan (모델 · DTO · mapper · API · queries · 표시 UI)
  shared/       ui · map · api · lib · config
```

## 아직 연결하지 않은 것

- 로그인 · 회원가입 · `proxy.ts` 접근 제어 (mock은 항상 ‘서연’ 계정으로 응답)
- 카카오 지도 SDK: 지금은 `shared/map`의 개념 지도 일러스트. 화면은 `MapView` props만 쓰므로 `shared/map` 안에서 교체합니다.
- 사진 업로드 API: 기기에서 고른 사진은 브라우저 임시 URL이라 새로고침하면 사라집니다.
- 이동 시간 · 영업시간 · 추천은 예시 데이터와 계산식입니다. 실제 길찾기·장소 데이터 연동 전입니다.
