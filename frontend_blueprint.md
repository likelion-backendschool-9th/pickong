# 찜콩 (Pickong) — Frontend Blueprint

`찜콩_기획서.pdf`를 바탕으로 정리한 **프론트엔드 설계 문서**입니다.
백엔드(Spring Boot) 없이 프론트엔드 기본 틀을 먼저 만들고, 이후 실제 API로 전환하는 것을 전제로 합니다.

> 프론트·백엔드 공통 내용(설계 기준, 핵심 사용자 흐름, API 계약, 기능 정리)은 [common_blueprint.md](common_blueprint.md),
> 백엔드 구조는 [backend_blueprint.md](backend_blueprint.md)에 있습니다.

---

## 0. 기술 스택 · 디자인 방향

| 구분 | 선택 | 비고 |
| --- | --- | --- |
| 프레임워크 | Next.js (App Router) | Next.js 16 기준 (`proxy.ts`). 15 이하라면 `middleware.ts` |
| UI | React | Server Component 기본, 필요한 곳만 Client Component |
| 언어 | TypeScript (strict) | |
| 스타일 | Tailwind CSS | 디자인 토큰은 `app/globals.css`의 `@theme` 한 곳에서 정의 |
| 서버 상태 | TanStack Query | **클라이언트에서 다시 조회·변경되는 데이터에만** 사용 (8장) |
| 클라이언트 전역 상태 | Zustand | **초기에는 사용하지 않음.** 도입 조건은 8장 |
| 지도 | Naver Maps JS SDK | `shared/map` 안에서만 SDK를 직접 사용 (`shared/map/naver/`) |
| 폼 | React Hook Form + Zod | 스키마는 폼 옆에 둠 |
| Mock | MSW 기반 독립 mock 서버 (제안) | API 주소만 바꿔서 실제 백엔드로 전환 |
| 테스트 | Vitest + Testing Library, Playwright (제안) | 순수 로직 단위 테스트 우선 |
| 구조 검사 | ESLint + `eslint-plugin-boundaries` (제안) | 계층 의존 규칙을 리뷰가 아닌 lint로 강제 |
| 백엔드 | Spring Boot + PostgreSQL/PostGIS | [backend_blueprint.md](backend_blueprint.md) |

**디자인 방향 (확정):** 흰색 바탕의 평면적인 모바일 웹 UI. 사진이 주요 콘텐츠이고, 주요 액션과 선택 상태에만 라임 계열을 씁니다. 폰트는 Noto Sans KR, 기준 화면 390 × 844 (360~480px 유동, 데스크톱은 가운데 열).
색상 값은 `app/globals.css`의 `@theme` 한 곳에서만 정의하고, 컴포넌트에는 토큰 이름만 씁니다 (hex 직접 사용 금지).

| 토큰 | 값 | 역할 |
| --- | --- | --- |
| `bg` | `#FFFFFF` | 메인 배경 · 바텀시트 · 헤더 · 하단 내비게이션 |
| `soft` | `#F7FCEB` | 라임 화이트: 설명 영역 · 안내 카드 · 위치 확인 배경 · 입력창 |
| `selected` | `#F1FAD9` | 라임 크림: 체크박스 선택 배경 · 선택된 칩 · 작은 선택 요소 |
| `primary` | `#84CC16` | 메인 라임그린: 주요 CTA · 브랜드 포인트 · 핀 |
| `primary-deep` | `#4D7C0F` | 딥 그린: 체크 표시 · 연한 라임 위 강조 텍스트 · 선택 아이콘 |
| `on-primary` | `#1A2E05` | 라임그린 위 텍스트 (흰색은 대비 부족) |
| `ink` / `ink-sub` / `ink-faint` / `line` | 중립색 | 본문 / 부가 설명 / 메타 / 구분선 |
| `warning` · `danger` (+ `-soft`) | 앰버 · 레드 | 경고 · 오류. 일반 선택 상태(라임)와 섞지 않음 |

핵심 사용자 흐름(발견 → 저장 → 계획 → 방문 → 공유)은 [common_blueprint.md](common_blueprint.md) 4장에 있습니다.

---

## 1. 기존 구조의 장점과 문제점

### 장점
- `app/`은 라우팅만 담당한다는 방향이 있었다.
- 도메인 단위로 코드를 묶으려 했다.
- API 호출 레이어를 컴포넌트와 분리하려 했다.
- 지도 SDK를 adapter로 감싸서 교체 가능성을 고려했다.
- 기능을 기획서 출시 단계별로 정리해서 범위가 명확했다.

### 문제점

| # | 문제 | 유지보수에서 생기는 일 |
| --- | --- | --- |
| 1 | **모든 UI가 `features/`에 들어감.** `PlanEditor`, `CommentList`, 링크 가져오기 화면 전체처럼 한 페이지에서만 쓰는 코드도 feature로 분리 | 페이지 하나를 고칠 때 `app/`과 `features/`를 오가야 함. `features/`가 사실상 페이지 폴더의 복제본이 됨 |
| 2 | **feature 간 조합 규칙이 없음.** `FeedCard`(feed) 안에 `SavePlaceButton`(place)을 넣으면서 "index.ts면 import 허용"이라는 예외를 둠 | feature끼리 서로 import하게 되고 순환 의존이 생김. 한 feature를 고치면 어디가 깨질지 예측할 수 없음 |
| 3 | **전역 `types/` 폴더.** 도메인 타입의 소유자가 없고, API 응답(DTO)과 화면용 모델 구분이 없음 | 백엔드 필드명 하나가 바뀌면 그 타입을 쓰는 모든 컴포넌트를 수정해야 함 |
| 4 | **API 함수마다 `if (USE_MOCK)` 분기** | 코드 경로가 둘이라 실제 경로가 테스트되지 않음. 백엔드 연결 시 모든 API 파일을 다시 손대야 함 |
| 5 | **Server/Client 구분 없음.** 모든 데이터를 훅으로 가져오는 구조, `loading`/`error` 처리 없음, 공유 링크용 공개 페이지·SEO 미고려 | 결국 모든 페이지가 Client Component가 되기 쉬움. 첫 화면이 느리고 공유 링크 미리보기가 안 됨 |
| 6 | **Zustand `authStore`, `uiStore`** | 세션의 원본은 서버 쿠키인데 클라이언트 store에 복제하면 둘이 어긋남. 바텀시트 열림 같은 상태는 지역 상태로 충분함 |
| 7 | **일정 계산 로직의 경계가 불명확.** `validatePlan.ts`가 훅들과 같은 feature 안에 섞여 있음 | 계산 로직이 컴포넌트·훅에 엉키기 쉽고, 단위 테스트를 하기 어려움 |
| 8 | **지도 adapter가 `features/map` 안에 있음** | 컬렉션·일정·링크 가져오기 화면도 지도가 필요한데, 그러려면 다른 feature의 내부에 의존해야 함. 지도는 도메인과 무관한 인프라임 |
| 9 | **`components/layout`이 전역에 있음** | 헤더·하단 탭은 `(main)` 레이아웃에서만 쓰는데 전역에 있어서 사용 범위가 드러나지 않음 |

---

## 2. 유지할 부분과 변경할 부분

### 유지

| 항목 | 이유 |
| --- | --- |
| 기술 스택, 디자인 방향 | 이미 결정된 사항 |
| 기능 요구사항, 출시 단계, 라우트 | 기획서 기준. 기능은 [common_blueprint.md](common_blueprint.md) 부록 A, 라우트는 이 문서 부록 B에 보존 |
| 도메인 단위로 묶는 방식 | 담당자별로 병렬 개발하기 좋음 |
| API 레이어 분리 | 백엔드 계약이 바뀔 때 영향을 한곳에 가둠 |
| 지도 SDK 경계 | 위치만 `shared/map`으로 옮기고 유지 |

### 변경

| 변경 | 이유 |
| --- | --- |
| **사용 범위에 따라 배치** (페이지 → 상위 라우트 → features/widgets → shared) | 페이지 전용 코드는 페이지 옆에 두어 찾기 쉽게 하고, 진짜 재사용되는 코드만 위로 올림 |
| **FSD 계층 일부 도입:** `app` · `widgets` · `features` · `entities` · `shared`. 단, **필요한 슬라이스만 생성** | 의존 방향을 한 방향으로 고정. feature끼리의 의존을 금지하고 조합은 위 계층에서 함 |
| **`widgets/`는 1개(`feed-card`)로 시작** | 여러 페이지에서 똑같이 조합되는 블록이 현재 이것뿐이기 때문 |
| **전역 `types/` 삭제** → 엔티티별 `model.ts` + `api/dto.ts` + `api/mapper.ts` | 타입마다 소유자가 생기고, DTO 변경이 mapper에서 멈춤 |
| **mock은 HTTP 계층에서** (독립 mock 서버) | API 코드가 한 경로가 됨. 전환은 환경변수 하나 |
| **`page.tsx`는 항상 Server Component** | 첫 화면 렌더링, SEO, 서버 전용 코드 보호 |
| **라우트별 `loading.tsx` / `error.tsx` / `not-found.tsx`** | 데이터를 가져오는 페이지의 대기·오류 처리를 표준화 |
| **Zustand 초기 미사용** | 세션은 서버 → Context로 읽기 전용 전달. 나머지는 지역 상태와 URL 상태로 해결 |
| **일정 계산 로직을 순수 함수로 분리** (`entities/plan/model`) | React 없이 단위 테스트 가능. 화면(드래그 라이브러리 등)을 바꿔도 규칙은 그대로 |
| **지도는 `shared/map`**, 네이버 SDK는 `shared/map/naver/` 안에서만 | 모든 페이지가 같은 지도 컴포넌트를 쓰고, SDK 교체 영향은 폴더 하나로 끝남 |

---

## 3. 개선한 전체 폴더 구조

> `(2차)`, `(3차)`는 해당 출시 단계에 생기는 파일입니다. **지금 만들지 않습니다.**
> 폴더 안의 파일 목록은 예시이며, 실제로 필요할 때 만듭니다. 비어 있는 계층 폴더를 미리 만들지 않습니다.

```
pickong/
├── README.md                               # 저장소 안내 · 실행 방법
├── common_blueprint.md                     # 프론트·백엔드 공통 설계
├── frontend_blueprint.md                   # 이 문서
├── backend_blueprint.md                    # 백엔드 설계
├── .gitignore · .editorconfig              # 프론트·백엔드 공통 설정
├── backend/                                # Spring Boot (backend_blueprint.md)
└── frontend/
    ├── public/
    │   ├── images/                         # logo.svg, mascot.png
    │   └── icons/pins/                     # 카테고리별 핀 아이콘
    │
    ├── mocks/                              # 개발용 mock 서버 (앱 번들에 포함되지 않음)
    │   ├── fixtures/                       # DTO 형태 샘플 데이터 (users, places, posts, collections, plans, imports)
    │   │   └── placeSubmissions.ts         # 장소 제보 시드. 관리자 화면은 이 mock을 씀
    │   ├── handlers/                       # 리소스별 요청 핸들러 (API 계약 기준)
    │   │   └── placeSubmissions.ts         # 제보 목록·상세·승인·반려·접수. 실제 API와 같은 경로
    │   └── server.ts                       # localhost:4000. placeSubmissionRoutes 등록
    │
    ├── e2e/                                # Playwright 핵심 흐름 테스트 (1차 후반)
    │
    ├── src/
    │   ├── proxy.ts                        # 로그인 필요 경로 접근 제어 (Next 15 이하: middleware.ts)
    │   │
    │   ├── app/                            # ── 라우팅 + 페이지 전용 코드
    │   │   ├── layout.tsx                  # Server: html, 폰트, 세션 조회 → Providers
    │   │   ├── providers.tsx               # Client: QueryClientProvider, SessionProvider
    │   │   ├── globals.css                 # Tailwind + 디자인 토큰(@theme)
    │   │   ├── global-error.tsx
    │   │   ├── not-found.tsx
    │   │   ├── (auth)/
    │   │   │   ├── page.tsx                # / 시작 화면 (공개). 시작하기 → /login. 로그인 상태면 /feed로
    │   │   │   ├── _components/AuthUi.tsx  # 시작·로그인·온보딩 공통 입력창·체크박스·알약 버튼
    │   │   │   ├── login/                  # 로그인 · 회원가입 통합 (카카오 · Google)
    │   │   │   │   ├── page.tsx                # Server: 내 지도 그림 + 소셜 로그인 버튼(<a>)
    │   │   │   │   ├── _lib/loginError.ts      # ?error= 사유 코드 → 안내 문구
    │   │   │   │   └── _components/            # LoginHero, SocialLoginButton, LoginErrorNotice(Client)
    │   │   │   └── signup/                 # 최초 가입 온보딩 (소셜 인증을 마친 신규 회원만)
    │   │   │       ├── page.tsx
    │   │   │       ├── _api/signupApi.ts       # 가입 세션 조회 · 완료 · 그만두기
    │   │   │       ├── _lib/signup.ts          # 단계 순서, 닉네임 기본값, 약관 동의
    │   │   │       └── _components/
    │   │   │           ├── SignupFlow.tsx      # 닉네임 → 프로필 사진 · 이름(건너뛰기) → 약관 동의 시트
    │   │   │           ├── NicknameStep.tsx · ProfileStep.tsx · TermsSheet.tsx
    │   │   │           └── SignupUi.tsx        # 단계 화면 틀, o──o──o 진행 표시
    │   │   │
    │   │   ├── (admin)/                    # 관리자
    │   │   │   └── admin/
    │   │   │       ├── page.tsx            # /admin
    │   │   │       ├── AdminDashboard.tsx  # 제보 관리 버튼 → /admin/place-submissions
    │   │   │       └── place-submissions/  # 목록·상세. mock 배열을 조회
    │   │   │           ├── page.tsx        # /admin/place-submissions
    │   │   │           ├── PlaceSubmissionsScreen.tsx  # 상태 집계·탭, 장소명·제보자 검색, 카테고리·정렬
    │   │   │           ├── _components/
    │   │   │           │   └── SubmissionStatusBadge.tsx
    │   │   │           ├── _lib/
    │   │   │           │   ├── filterSubmissions.ts
    │   │   │           │   └── filterSubmissions.test.ts
    │   │   │           └── [id]/
    │   │   │               ├── page.tsx    # /admin/place-submissions/[id]
    │   │   │               └── PlaceSubmissionDetailScreen.tsx  # 승인·반려
    │   │   │
    │   │   └── (main)/                     # 앱 셸 레이아웃 (헤더 + 하단 탭)
    │   │       ├── layout.tsx
    │   │       ├── error.tsx               # (main) 공통 오류 화면
    │   │       ├── _components/            # (main) 전체에서만 쓰는 것
    │   │       │   ├── AppHeader.tsx
    │   │       │   ├── BottomTabBar.tsx
    │   │       │   ├── SideNav.tsx         # 데스크톱
    │   │       │   ├── ComposeMenu.tsx     # ➕ 게시물 작성 / 링크 가져오기
    │   │       │   └── LogoutButton.tsx    # 헤더 메뉴 + settings에서 사용
    │   │       │
    │   │       ├── feed/
    │   │       │   ├── page.tsx
    │   │       │   ├── loading.tsx
    │   │       │   └── _components/
    │   │       │       ├── FeedList.tsx
    │   │       │       └── FeedEmpty.tsx
    │   │       │
    │   │       ├── explore/
    │   │       │   ├── page.tsx
    │   │       │   ├── loading.tsx
    │   │       │   └── _components/
    │   │       │       ├── PopularCollections.tsx
    │   │       │       ├── CreatorList.tsx
    │   │       │       └── FriendsPicks.tsx
    │   │       │
    │   │       ├── map/
    │   │       │   ├── page.tsx
    │   │       │   ├── loading.tsx
    │   │       │   ├── _components/
    │   │       │   │   ├── MyMap.tsx
    │   │       │   │   ├── MapFilterBar.tsx
    │   │       │   │   ├── MapSearch.tsx
    │   │       │   │   └── PlaceSummarySheet.tsx
    │   │       │   └── _lib/
    │   │       │       ├── filterSavedPlaces.ts
    │   │       │       └── filterSavedPlaces.test.ts
    │   │       │
    │   │       ├── posts/
    │   │       │   ├── new/
    │   │       │   │   ├── page.tsx
    │   │       │   │   └── _components/
    │   │       │   │       ├── PostEditor.tsx
    │   │       │   │       ├── PhotoUploader.tsx
    │   │       │   │       └── postSchema.ts
    │   │       │   └── [postId]/
    │   │       │       ├── page.tsx
    │   │       │       ├── loading.tsx
    │   │       │       ├── not-found.tsx
    │   │       │       ├── _components/
    │   │       │       │   ├── CommentSection.tsx
    │   │       │       │   └── CommentInput.tsx
    │   │       │       └── _api/
    │   │       │           └── comments.ts     # 댓글 API + DTO + mapper (이 페이지에서만 사용)
    │   │       │
    │   │       ├── places/
    │   │       │   ├── new/                    # 실제 파일은 (main)/(sub)/places/new
    │   │       │   │   ├── page.tsx            # /places/new
    │   │       │   │   └── NewPlaceScreen.tsx  # 좌표를 고른 뒤 CustomPlaceFlow mode=report
    │   │       │   ├── [placeId]/
    │   │       │   │   ├── page.tsx
    │   │       │   │   ├── loading.tsx
    │   │       │   │   ├── not-found.tsx
    │   │       │   │   └── _components/
    │   │       │   │       ├── PlaceHeader.tsx
    │   │       │   │       ├── BusinessHoursTable.tsx
    │   │       │   │       ├── FriendReviews.tsx
    │   │       │   │       └── PlacePosts.tsx
    │   │       │   └── custom/new/                 # (2차)
    │   │       │       ├── page.tsx
    │   │       │       └── _components/
    │   │       │           ├── CustomPlaceForm.tsx
    │   │       │           └── CoordinatePicker.tsx
    │   │       │
    │   │       ├── collections/
    │   │       │   ├── page.tsx                    # 내 컬렉션 목록
    │   │       │   ├── loading.tsx
    │   │       │   ├── new/
    │   │       │   │   ├── page.tsx
    │   │       │   │   └── _components/
    │   │       │   │       ├── CollectionForm.tsx  # 편집 화면이 생기면 collections/_components로 승격
    │   │       │   │       └── PinStylePicker.tsx
    │   │       │   └── [collectionId]/             # 공개 컬렉션 공유 링크 (공개, SEO)
    │   │       │       ├── page.tsx
    │   │       │       ├── loading.tsx
    │   │       │       ├── not-found.tsx
    │   │       │       └── _components/
    │   │       │           ├── CollectionMap.tsx
    │   │       │           ├── CollectionPlaceList.tsx
    │   │       │           └── ShareLinkButton.tsx
    │   │       │
    │   │       ├── import/                         # AI 외부 링크 장소 가져오기 (이 페이지에서만 사용)
    │   │       │   ├── page.tsx
    │   │       │   ├── _components/
    │   │       │   │   ├── ImportFlow.tsx          # 단계 전환: 입력 → 진행 → 검토 → 저장
    │   │       │   │   ├── UrlInputForm.tsx
    │   │       │   │   ├── ManualTextInput.tsx
    │   │       │   │   ├── ExtractProgress.tsx
    │   │       │   │   ├── CandidateList.tsx
    │   │       │   │   ├── CandidateItem.tsx
    │   │       │   │   ├── CandidateEditSheet.tsx
    │   │       │   │   ├── CandidateMapPreview.tsx
    │   │       │   │   └── BulkSaveBar.tsx
    │   │       │   ├── _lib/
    │   │       │   │   ├── candidateSelection.ts   # 선택·수정·중복 처리 순수 로직
    │   │       │   │   └── candidateSelection.test.ts
    │   │       │   └── _api/
    │   │       │       ├── dto.ts
    │   │       │       ├── mapper.ts
    │   │       │       ├── importApi.ts
    │   │       │       └── queries.ts              # 추출 작업 상태 polling
    │   │       │
    │   │       ├── plans/
    │   │       │   ├── page.tsx                    # 일정 목록 (예정 / 지난 일정)
    │   │       │   ├── loading.tsx
    │   │       │   ├── _components/
    │   │       │   │   └── PlanListSection.tsx
    │   │       │   ├── new/
    │   │       │   │   ├── page.tsx                # 날짜·제목 입력 → 생성 후 /plans/[planId]로 이동
    │   │       │   │   └── _components/NewPlanForm.tsx
    │   │       │   └── [planId]/                   # 일정 상세 · 편집 · 점검
    │   │       │       ├── page.tsx
    │   │       │       ├── loading.tsx
    │   │       │       ├── not-found.tsx
    │   │       │       ├── _components/
    │   │       │       │   ├── PlanEditor.tsx
    │   │       │       │   ├── PlanTimeline.tsx
    │   │       │       │   ├── StopItem.tsx
    │   │       │       │   ├── SavedPlacePickerSheet.tsx
    │   │       │       │   ├── RouteMap.tsx
    │   │       │       │   ├── TravelTimeBadge.tsx
    │   │       │       │   ├── PlanCheckPanel.tsx      # (2차)
    │   │       │       │   └── WeatherBadge.tsx        # (3차)
    │   │       │       ├── _hooks/
    │   │       │       │   ├── usePlanEditor.ts
    │   │       │       │   └── useTravelTimes.ts
    │   │       │       └── _lib/
    │   │       │           ├── planEditorReducer.ts
    │   │       │           └── planEditorReducer.test.ts
    │   │       │
    │   │       ├── users/[username]/               # 프로필 (공개, SEO)
    │   │       │   ├── page.tsx                    # ?tab=posts|collections|map
    │   │       │   ├── loading.tsx
    │   │       │   ├── not-found.tsx
    │   │       │   ├── _components/
    │   │       │   │   ├── ProfileHeader.tsx
    │   │       │   │   ├── ProfileTabs.tsx
    │   │       │   │   └── UserList.tsx            # followers/following 공용 → 공통 부모에 위치
    │   │       │   ├── followers/page.tsx
    │   │       │   └── following/page.tsx
    │   │       │
    │   │       ├── notifications/                  # (3차)
    │   │       │   ├── page.tsx
    │   │       │   └── _components/NotificationList.tsx
    │   │       │
    │   │       └── settings/
    │   │           ├── page.tsx                    # 계정 · 기본 공개 범위
    │   │           └── profile/
    │   │               ├── page.tsx
    │   │               └── _components/ProfileEditForm.tsx
    │   │
    │   ├── widgets/                        # ── 여러 페이지에서 똑같이 조합되는 UI 블록 (현재 1개)
    │   │   └── feed-card/
    │   │       ├── index.ts
    │   │       └── FeedCard.tsx            # FeedCardView + LikeButton + SavePlaceButton 조합
    │   │
    │   ├── features/                       # ── 여러 페이지에서 재사용하는 사용자 행동
    │   │   ├── save-place/                 # "찜" → 컬렉션 선택 → 저장
    │   │   │   ├── index.ts
    │   │   │   ├── SavePlaceButton.tsx
    │   │   │   ├── SaveToCollectionSheet.tsx
    │   │   │   └── useSavePlace.ts
    │   │   ├── like-feed/
    │   │   │   ├── index.ts
    │   │   │   ├── LikeButton.tsx
    │   │   │   └── useLikeFeed.ts
    │   │   ├── follow-user/
    │   │   │   ├── index.ts
    │   │   │   ├── FollowButton.tsx
    │   │   │   └── useFollowUser.ts
    │   │   ├── place-search/
    │   │   │   ├── index.ts
    │   │   │   ├── PlaceSearchInput.tsx
    │   │   │   ├── PlaceSearchResults.tsx
    │   │   │   └── usePlaceSearch.ts
    │   │   └── create-custom-place/        # 좌표 장소 입력. mode=report면 제보, 아니면 개인 저장
    │   │       ├── index.ts
    │   │       └── CustomPlaceFlow.tsx
    │   │
    │   ├── entities/                       # ── 도메인 모델 + API 계약 + 표시 전용 UI
    │   │   ├── user/
    │   │   │   ├── index.ts
    │   │   │   ├── model.ts
    │   │   │   ├── api/                    # dto.ts, mapper.ts, userApi.ts, queries.ts
    │   │   │   └── ui/                     # UserAvatar.tsx, UserRow.tsx
    │   │   ├── place/
    │   │   │   ├── index.ts
    │   │   │   ├── model.ts                # Place, SavedPlace, PlaceCategory, BusinessHours
    │   │   │   ├── categories.ts           # 카테고리 → 핀 색상·아이콘 매핑
    │   │   │   ├── businessHours.ts        # 영업 여부·휴무일 판정 (순수 함수)
    │   │   │   ├── businessHours.test.ts
    │   │   │   ├── api/
    │   │   │   └── ui/                     # PlaceCard.tsx, PlacePin.tsx, CategoryIcon.tsx
    │   │   ├── place-submission/           # 장소 제보. 공개 Place와 별개. 목록 응답은 배열
    │   │   │   ├── index.ts
    │   │   │   ├── model.ts                # PlaceSubmission, PlaceSubmissionStatus, PlaceSubmissionSubmitter
    │   │   │   └── api/                    # dto.ts, mapper.ts, mapper.test.ts, placeSubmissionApi.ts, queries.ts
    │   │   ├── feed/
    │   │   │   ├── index.ts
    │   │   │   ├── model.ts
    │   │   │   ├── api/
    │   │   │   └── ui/                     # FeedCardView.tsx, SponsoredBadge.tsx
    │   │   ├── collection/
    │   │   │   ├── index.ts
    │   │   │   ├── model.ts
    │   │   │   ├── api/
    │   │   │   └── ui/                     # CollectionCard.tsx
    │   │   └── plan/
    │   │       ├── index.ts
    │   │       ├── model/                  # 로직이 많아서 폴더로 분리 (다른 엔티티는 model.ts 한 파일)
    │   │       │   ├── types.ts            # Plan, PlanStop, TimelineEntry, PlanWarning
    │   │       │   ├── reorder.ts          # 순서 변경 (고정 일정 규칙 포함)
    │   │       │   ├── timeline.ts         # 시간표 계산
    │   │       │   ├── validate.ts         # 휴무일·영업시간·이동 시간 점검
    │   │       │   ├── optimize.ts         # (2차) 동선 최적화
    │   │       │   └── *.test.ts
    │   │       └── api/
    │   │
    │   └── shared/                         # ── 도메인을 모르는 범용 코드
    │       ├── ui/                         # Button, Input, Textarea, Select, Modal, BottomSheet,
    │       │                               # Tabs, Avatar, Badge, Chip, Toast, Skeleton, Spinner,
    │       │                               # EmptyState, ImageGrid, VisibilitySelect
    │       ├── map/                        # 지도 컴포넌트 (카카오 SDK 경계)
    │       │   ├── index.ts
    │       │   ├── types.ts                # LatLng, Bounds, MapViewport
    │       │   ├── Map.tsx
    │       │   ├── MapMarker.tsx
    │       │   ├── MarkerCluster.tsx
    │       │   └── naver/                  # 네이버 SDK를 아는 유일한 폴더
    │       ├── api/
    │       │   ├── http.ts                 # fetch 래퍼 (서버/브라우저 공용)
    │       │   ├── errors.ts               # ApiError
    │       │   ├── pagination.ts           # Paginated<T>, CursorPage<T>
    │       │   └── queryClient.ts
    │       ├── auth/
    │       │   ├── authApi.ts              # logout (재발급은 http.ts가 401일 때 처리)
    │       │   ├── socialProviders.ts      # KAKAO · GOOGLE, 로그인 시작 주소
    │       │   ├── SocialLogo.tsx          # 제공자 로고 (public/icons)
    │       │   ├── session.server.ts       # 서버에서 세션 쿠키 읽기 (server-only)
    │       │   └── SessionProvider.tsx     # 현재 사용자 읽기 전용 Context
    │       ├── lib/                        # date.ts, time.ts, geo.ts(거리), cn.ts
    │       └── config/
    │           ├── env.ts                  # 환경변수 검증 (zod)
    │           ├── routes.ts               # 라우트 경로 상수. 제보 관리 /admin/place-submissions, /admin/place-submissions/[id]
    │           └── visibility.ts           # 공개 / 팔로워 공개 / 비공개 (플랫폼 공통 정책)
    │
    ├── .env.local.example
    ├── eslint.config.mjs                   # 계층 의존 규칙 포함
    ├── next.config.ts                      # /api/* → 백엔드(또는 mock) rewrites
    ├── tsconfig.json                       # paths: "@/*" → "src/*"
    ├── vitest.config.ts
    └── package.json
```

---

## 4. 각 계층과 디렉터리의 역할

| 계층 | 넣는 것 | 넣지 않는 것 | 언제 만드나 |
| --- | --- | --- | --- |
| **`app/`** | 라우트(`page`, `layout`, `loading`, `error`, `not-found`), 그 라우트에서만 쓰는 컴포넌트·훅·로직·API(`_components`, `_hooks`, `_lib`, `_api`), **여러 feature를 조합하는 일** | 두 개 이상의 서로 다른 라우트 영역에서 쓰는 코드 | 항상 |
| **`widgets/`** | 여러 페이지에서 **같은 방식으로** 반복 조합되는 UI 블록 (엔티티 UI + 여러 feature) | 한 페이지에서만 쓰는 조합 (→ 페이지 `_components`) | 같은 조합이 두 번째 페이지에 필요해질 때 |
| **`features/`** | 여러 페이지에서 쓰는 **사용자 행동** 하나 (찜하기, 좋아요, 팔로우, 장소 검색). UI + 상태 변경 로직 | 한 페이지 전용 기능, 다른 feature를 import하는 코드 | 같은 행동이 두 번째 라우트 영역에 필요해질 때 |
| **`entities/`** | 도메인 모델 타입, API 함수·DTO·mapper·queryOptions, 도메인 순수 로직, 표시 전용 UI | 데이터 변경을 일으키는 UI(→ features), 페이지별 화면 로직 | 백엔드 리소스가 여러 페이지에서 쓰일 때 |
| **`shared/`** | 도메인을 모르는 UI, 지도, HTTP 클라이언트, 세션, 유틸, 설정 | `Place`, `Post` 같은 도메인 타입을 import하는 코드 | 처음부터 (단, 실제로 쓰는 것만) |

### 세부 디렉터리

| 위치 | 역할 |
| --- | --- |
| `app/**/_components` | 그 라우트(와 하위 라우트) 전용 컴포넌트. `_`로 시작하는 폴더는 Next.js 라우팅에서 제외됨 |
| `app/**/_hooks` | 그 라우트 전용 훅. **파일이 2개 이상일 때만** 폴더 생성, 하나면 `_components` 안에 둬도 됨 |
| `app/**/_lib` | 그 라우트 전용 순수 로직 (React import 금지). 테스트 파일을 바로 옆에 둠 |
| `app/**/_api` | 그 라우트에서만 쓰는 백엔드 리소스의 API·DTO·mapper (예: 댓글, 링크 추출) |
| `entities/*/model.ts` | 프론트 도메인 모델. 컴포넌트는 이 타입만 사용 |
| `entities/*/api/dto.ts` | 백엔드 응답 형태 그대로. **`api/` 폴더 밖으로 export하지 않음** |
| `entities/*/api/mapper.ts` | DTO ↔ 도메인 모델 변환 |
| `entities/*/api/*Api.ts` | 실제 HTTP 호출. 반환값은 항상 도메인 모델 |
| `entities/*/api/queries.ts` | TanStack Query의 query key + `queryOptions`. **커스텀 훅으로 감싸지 않음** |
| `entities/*/ui` | props를 받아 그리기만 하는 컴포넌트. 데이터 조회·변경 금지. 행동 버튼은 `actions` 같은 slot으로 받음 |
| `shared/map/naver` | 네이버 SDK 로딩, 좌표 변환, 실제 지도 구현 |
| `mocks/` | 개발·테스트용 가짜 API. `src` 밖에 있어서 앱 번들에 포함되지 않음 |

### 왜 이 계층들만 쓰는가

- **`pages` 계층(FSD)은 만들지 않음:** Next.js의 `app/`이 그 역할을 함. 두 개를 두면 페이지 코드가 두 곳으로 나뉨.
- **`processes` 계층은 만들지 않음:** FSD에서도 사용을 권장하지 않으며, 여러 단계 흐름(링크 가져오기)은 한 페이지 안에서 끝남.
- **`widgets`는 남김:** `FeedCard`(엔티티 UI + 좋아요 + 찜)가 피드·프로필·장소 상세·탐색 네 곳에서 똑같이 쓰임. 각 페이지에서 따로 조합하면 카드 하나를 바꿀 때 네 곳을 고쳐야 함.
- **`entities`는 남김:** feature끼리 import하지 않으려면, 여러 feature가 함께 쓰는 모델·API를 둘 아래 계층이 필요함.

---

## 5. 계층 및 Feature 간 의존성 규칙

### 의존 방향

```
app  ──▶  widgets  ──▶  features  ──▶  entities  ──▶  shared
 │           │              │               ▲
 └───────────┴──────────────┴───────────────┘   (위 계층은 아래 계층 어디든 import 가능)
```

**위 계층만 아래 계층을 import할 수 있습니다.** 반대 방향은 금지합니다.

### import 허용표

| import 하는 쪽 ↓ / 대상 → | app | widgets | features | entities | shared |
| --- | :-: | :-: | :-: | :-: | :-: |
| **app** | 라우트 규칙 ※1 | ✅ | ✅ | ✅ | ✅ |
| **widgets** | ❌ | ❌ | ✅ | ✅ | ✅ |
| **features** | ❌ | ❌ | ❌ **(다른 feature 금지)** | ✅ | ✅ |
| **entities** | ❌ | ❌ | ❌ | ⚠️ ※2 | ✅ |
| **shared** | ❌ | ❌ | ❌ | ❌ | ✅ |

※1 **라우트 간 규칙:** 어떤 라우트의 `_components`·`_lib` 등은 **그 라우트와 하위 라우트만** import할 수 있습니다. 형제 라우트끼리는 서로의 `_` 폴더를 import하지 않습니다. 둘 다 필요하면 가장 가까운 공통 부모로 옮깁니다.
- 예: `followers/page.tsx`와 `following/page.tsx`가 함께 쓰는 `UserList` → `users/[username]/_components/`

※2 **엔티티 간 참조:** 타입과 순수 함수에 한해, **정해진 방향으로만** 다른 엔티티의 공개 API를 import할 수 있습니다. 순환은 금지합니다.

```
feed ──▶ user, place
collection ──▶ user, place
plan ──▶ place
```

### feature끼리 조합해야 할 때

feature A가 feature B를 import하지 않습니다. **조합은 위 계층에서 합니다.**

```tsx
// ❌ features/like-feed 안에서 features/save-place를 import
// ✅ widgets/feed-card/FeedCard.tsx에서 조합
<FeedCardView feed={feed} actions={<><LikeButton feedId={feed.id} /><SavePlaceButton place={feed.place} /></>} />
```

조합이 한 페이지에서만 일어나면 widget을 만들지 않고 그 페이지의 `_components`에서 조합합니다 (예: `map/_components/MapSearch.tsx`가 `place-search`와 `save-place`를 조합).

### 공개 API(`index.ts`) 적용 범위

| 위치 | 규칙 |
| --- | --- |
| `widgets/*`, `features/*`, `entities/*` | **슬라이스마다 `index.ts` 필수.** 밖에서는 `@/entities/place`처럼 index로만 import. `@/entities/place/api/mapper` 같은 내부 경로 import 금지 |
| 슬라이스 내부 | 상대 경로 import. 자기 자신의 index를 import하지 않음 (순환 방지) |
| `shared/map` | 카카오 구현을 숨겨야 하므로 `index.ts` 사용 |
| `shared/ui`, `shared/lib` 등 나머지 shared | **index 없이 파일 경로로 import** (`@/shared/ui/Button`). 큰 barrel 파일은 클라이언트 번들을 키우고 순환 의존을 만들기 쉬움 |
| `app/` | index 만들지 않음 |
| 서버 전용 코드 | `*.server.ts` 파일은 `import 'server-only'`로 시작. **`index.ts`에서 다시 export하지 않음.** 서버 전용 export가 필요한 슬라이스는 별도 `server.ts` 진입점을 둠 |

`index.ts`에는 **밖에서 실제로 쓰는 것만** export합니다. DTO와 mapper는 export하지 않습니다.
예외: ※2 방향(feed·collection → user·place, plan → place)의 엔티티가 응답 안에 포함된 다른 엔티티를 변환할 때 필요한 DTO 타입과 mapper(`PlaceDto`·`toPlace`, `UserDto`·`toUser` 등)는 export합니다. 중첩 객체 변환을 엔티티마다 따로 구현하지 않기 위해서입니다.

### 규칙 강제 방법

- `eslint-plugin-boundaries`로 계층 간 import 방향 검사
- `no-restricted-imports`로 슬라이스 내부 경로(`@/entities/*/*`) import 금지
- `naver` / `kakao` 등 지도 SDK 전역 객체 사용은 `shared/map/{provider}/**`에서만 허용
- 서버 전용 모듈은 `server-only` 패키지로 클라이언트 import 시 빌드 실패

---

## 6. 페이지 전용 코드와 재사용 코드의 배치 기준

### 승격 사다리

새 코드는 **가장 좁은 범위에서 시작**하고, 실제로 두 번째 사용처가 생겼을 때 한 단계씩 올립니다.

```
① 그 페이지의 _components / _lib          (한 라우트에서만 사용)
        │  형제 라우트에서도 필요해짐
        ▼
② 공통 부모 라우트의 _components            (같은 라우트 영역 안에서 공유)
        │  다른 라우트 영역에서도 필요해짐
        ▼
③ features/ (행동)  ·  widgets/ (조합 블록)  ·  entities/ (모델·API·표시 UI)
        │  도메인과 무관해짐
        ▼
④ shared/
```

### 판단 순서

1. **도메인을 전혀 모르는가?** (버튼, 바텀시트, 날짜 포맷) → `shared/`
2. **백엔드 리소스의 모델·API·표시 전용 UI인가?** → 여러 페이지에서 쓰면 `entities/`, 한 페이지에서만 쓰면 그 페이지의 `_api/`
3. **사용자 행동(상태를 바꾸는 것)인가?** → 두 개 이상의 라우트 영역에서 쓰면 `features/`, 아니면 페이지 `_components`
4. **여러 entity·feature를 조합한 블록인가?** → 두 개 이상의 페이지에서 같은 방식으로 쓰면 `widgets/`, 아니면 페이지 `_components`
5. **그 외** → 페이지 `_components` / `_lib`

### 지금 각 위치에 있는 코드와 그 근거

| 코드 | 사용처 | 위치 |
| --- | --- | --- |
| `PlanEditor` 및 하위 컴포넌트 | `/plans/[planId]`만 | 페이지 `_components` |
| 링크 가져오기 화면 전체 | `/import`만 | 페이지 `_components`, `_api` |
| `CommentSection`, 댓글 API | `/posts/[postId]`만 | 페이지 `_components`, `_api` |
| `CollectionForm` | `/collections/new`만 (편집 화면 생기면 승격) | 페이지 `_components` |
| `UserList` | followers, following | 공통 부모 `users/[username]/_components` |
| `AppHeader`, `BottomTabBar`, `LogoutButton` | `(main)` 레이아웃, settings | `(main)/_components` |
| `FeedCard` (조합) | feed, users/[username], places/[placeId], explore | `widgets/feed-card` |
| `SavePlaceButton` | FeedCard, 장소 상세, 컬렉션 상세, 지도, 탐색 | `features/save-place` |
| `FollowButton` | 프로필, 팔로워 목록, 탐색 크리에이터, 게시물 상세 | `features/follow-user` |
| `LikeButton` | FeedCard, 게시물 상세 | `features/like-feed` |
| 장소 검색 | 게시물 작성(장소 태그), 지도, 링크 추출 후보 수정 | `features/place-search` |
| 영업시간 판정 | 장소 상세, 일정 점검 | `entities/place/businessHours.ts` |
| 일정 계산·검증 | 일정 편집, 일정 공유 화면(2차) — 9.3 참고 | `entities/plan/model` |
| 지도 컴포넌트 | 지도, 컬렉션, 일정, 링크 가져오기, 장소 상세, 커스텀 장소 | `shared/map` |

### 앞으로 생길 승격 후보 (지금 만들지 않음)

| 후보 | 승격 조건 |
| --- | --- |
| `features/visit-status` (방문 예정/완료 토글) | 2차에 지도 시트·장소 상세·컬렉션 목록에서 함께 쓰일 때 |
| `features/share-link` | 컬렉션에 이어 일정 공유(2차)에서도 필요할 때 |
| `widgets/place-sheet` | 지도와 컬렉션 상세의 장소 바텀시트가 같은 구성이 될 때 |
| `features/purchase-collection` | 유료 컬렉션(3차)이 컬렉션 상세와 크리에이터 페이지 양쪽에 생길 때 |
| `widgets/feed-list` | 피드·프로필·장소 상세의 게시물 목록이 같은 무한 스크롤 구성이 될 때 |

### 승격·강등 절차

- 승격은 **기능 변경 없이 파일 이동만 하는 PR**로 분리합니다 (리뷰가 쉬움).
- 예상만으로 미리 올리지 않습니다. "나중에 쓸 것 같다"는 승격 근거가 아닙니다.
- 위로 올린 코드가 결국 한 곳에서만 쓰이게 되면 다시 페이지로 내립니다.

---

## 7. Server / Client Component 구분 기준

### 기본 원칙

| 규칙 | 내용 |
| --- | --- |
| `page.tsx`, `layout.tsx` | **항상 Server Component.** `'use client'`를 붙이지 않음 |
| 데이터 조회 | 페이지(Server)에서 `entities/*/api`로 조회 → 직렬화 가능한 도메인 모델을 props로 전달 |
| `'use client'` | **트리의 말단에만.** 상태, 이벤트 핸들러, effect, 브라우저 API, 지도 SDK, 드래그, TanStack Query 훅이 필요한 컴포넌트 |
| 경계 | Client Component 안에 서버 데이터가 필요하면 부모(Server)가 props로 넘김. Client에서 서버 전용 모듈 import 금지 |

### 어떤 쪽으로 만들까

| Server Component | Client Component |
| --- | --- |
| 게시물 본문, 장소 정보, 프로필 소개, 컬렉션 설명 | 좋아요·찜·팔로우 버튼 |
| 영업시간 표, 정적인 목록 | 무한 스크롤 목록 |
| 메타데이터(`generateMetadata`) | 지도 전체 (`shared/map`은 모두 Client) |
| 빈 상태 안내, 레이아웃 뼈대 | 폼, 드래그 앤 드롭, 바텀시트, 탭 전환 |

### 서버 전용 / 브라우저 전용 코드의 경계

| 종류 | 위치 · 규칙 |
| --- | --- |
| 서버 전용 | `*.server.ts` + `import 'server-only'`. 예: `shared/auth/session.server.ts` (쿠키 읽기) |
| 서버 전용 환경변수 | `API_INTERNAL_URL` 등 `NEXT_PUBLIC_` 접두사 없는 변수. `shared/config/env.ts`에서만 읽음 |
| 브라우저 전용 | 카카오 SDK, `window`, `IntersectionObserver` → Client Component나 `useEffect` 안에서만 |
| 공용 | `entities/*/api` 함수, `shared/api/http.ts` — 서버·브라우저 양쪽에서 동작 |

### 라우트별 loading / error / not-found

| 파일 | 사용 기준 |
| --- | --- |
| `loading.tsx` | 서버에서 데이터를 가져오는 라우트마다 둠. 실제 레이아웃과 비슷한 스켈레톤 |
| `error.tsx` | `(main)/error.tsx` 하나로 시작. 특정 라우트가 다른 복구 방법(재시도 버튼 등)이 필요할 때만 추가 |
| `not-found.tsx` | `[id]` 동적 라우트마다 둠. API가 404를 주면 페이지에서 `notFound()` 호출 |
| `global-error.tsx` | 루트 레이아웃 자체가 실패할 때 |

`error.tsx`는 **렌더링 중 오류만** 잡습니다. Client에서 TanStack Query로 가져온 데이터의 오류는 그 컴포넌트에서 직접 처리하고, 변경(mutation) 실패는 토스트로 알립니다.

### 공개 페이지와 SEO

공유 링크로 열릴 수 있는 페이지는 **로그인 없이 볼 수 있어야** 합니다.

| 공개 | 로그인 필요 (`proxy.ts`에서 `/login`으로) |
| --- | --- |
| `/`, `/login`, `/signup`, `/terms/[type]` | `/feed`, `/map`, `/import`, `/plans/**`, `/terms/consent` |
| `/posts/[postId]` (공개 범위 준수) | `/posts/new`, `/collections`, `/collections/new` |
| `/places/[placeId]` | `/places/custom/new`, `/notifications`, `/settings/**` |
| `/collections/[collectionId]` (공개 컬렉션) | |
| `/users/[username]`, `/explore` | `/tags/**`, `/creator/**`, `/checkout/**`, `/purchases/**` |
| | `/admin/**` (관리자 계정만) |

- 공개 페이지는 `generateMetadata`로 제목·설명·OG 이미지를 설정합니다.
- 로그인하지 않은 사용자가 찜 같은 행동을 누르면 로그인 화면으로 보냅니다 (feature 컴포넌트에서 처리).
- 비공개 게시물·컬렉션은 백엔드가 403/404를 주고, 프론트는 `notFound()`로 처리합니다. **권한 판단은 백엔드가 담당**하고 프론트는 그 결과를 표시만 합니다.

---

## 8. 상태 관리 및 API 연동 규칙

### 8.1 상태 종류별 위치

| 상태 | 예 | 위치 · 도구 |
| --- | --- | --- |
| 서버 상태 (화면에 한 번 표시) | 장소 정보, 게시물 본문, 프로필 소개 | Server Component에서 조회 → props |
| 서버 상태 (클라이언트에서 다시 조회·변경) | 피드 무한 스크롤, 좋아요, 찜, 내 컬렉션 목록 | TanStack Query |
| URL 상태 | 지도 필터, 프로필 탭, 검색어 | `searchParams` (새로고침·공유해도 유지되고 서버에서도 읽을 수 있음) |
| 지역 UI 상태 | 바텀시트 열림, 선택된 마커 | `useState` (그 컴포넌트 안) |
| 복잡한 지역 상태 | 일정 편집 | `useReducer` + 순수 reducer (페이지 `_lib`) |
| 폼 상태 | 게시물 작성, 컬렉션 생성 | React Hook Form + Zod (폼 옆에 스키마) |
| 세션 (현재 사용자) | 로그인 사용자 정보 | 서버에서 쿠키로 조회 → `SessionProvider`(Context)로 읽기 전용 전달 |
| 전역 클라이언트 상태 | — | Zustand (**초기 0개**) |

### 8.2 Zustand 도입 조건

다음을 **모두** 만족할 때만 store를 만듭니다.
1. 서버 데이터가 아니다 (서버 데이터는 TanStack Query 담당).
2. URL로 표현하기 어렵다.
3. 페이지를 이동해도 유지되어야 하고, 서로 관련 없는 여러 컴포넌트가 함께 읽고 쓴다.

예상되는 첫 사용처: **이미지 업로드 진행 큐** (게시물 작성 후 다른 페이지로 이동해도 업로드 진행률이 보여야 하는 경우).
store를 만들면 그 store를 쓰는 기능 옆에 둡니다. 여러 기능이 쓰는 경우에만 `shared/`에 둡니다.
토스트는 `shared/ui/Toast` 내부에서 처리하고, 앱 코드는 `toast()` 함수만 호출합니다 (store를 드러내지 않음).

### 8.3 TanStack Query 사용 기준

| 사용함 | 사용하지 않음 |
| --- | --- |
| 무한 스크롤 (피드, 장소별 게시물) | 서버에서 한 번 그리고 클라이언트에서 바뀌지 않는 데이터 |
| 낙관적 업데이트가 필요한 변경 (좋아요, 찜, 팔로우) | 폼 입력값, 편집 중인 임시 상태 |
| 클라이언트에서 시작하는 조회 (장소 검색, 이동 시간) | 정적 설정값 (카테고리 목록 등) |
| polling (링크 추출 작업 진행 상태) | |
| 같은 데이터를 여러 컴포넌트가 보고 변경 후 함께 갱신되어야 할 때 (내 컬렉션 목록) | |

**서버에서 그리고, 클라이언트에서도 이어서 다루는 데이터** (피드 첫 페이지, 내 지도 장소): 페이지(Server)에서 같은 `queryOptions`로 `prefetchQuery` → `HydrationBoundary`로 전달. 클라이언트에서 같은 key로 이어받습니다.

**query key 관리:** 엔티티마다 `api/queries.ts`에 key와 `queryOptions`를 모읍니다. 변경 후 무효화도 이 key로 합니다.

```ts
// entities/place/api/queries.ts
export const savedPlaceKeys = {
  all: ['saved-places'] as const,
  list: (filter: SavedPlaceFilter) => [...savedPlaceKeys.all, 'list', filter] as const,
};
export const savedPlaceQueries = {
  list: (filter: SavedPlaceFilter) =>
    queryOptions({ queryKey: savedPlaceKeys.list(filter), queryFn: () => getSavedPlaces(filter) }),
};

// 사용하는 곳: 엔티티마다 useXxx 훅을 만들지 않고 바로 사용
const { data } = useQuery(savedPlaceQueries.list(filter));
```

**변경(mutation) 방식은 TanStack Query로 통일합니다.** Server Actions는 초기에 사용하지 않습니다.
- 백엔드가 Spring Boot라서 Next 서버를 한 번 더 거치는 이점이 적습니다.
- 두 방식을 섞으면 캐시 무효화 규칙이 두 갈래로 나뉘어 5명이 같은 규칙을 지키기 어렵습니다.
- 변경 후 서버에서 그린 부분도 갱신해야 하면 `router.refresh()`를 호출합니다.

### 8.4 타입 책임 구분

| 타입 | 위치 | 누가 사용 | 바뀌는 이유 |
| --- | --- | --- | --- |
| **API DTO** | `entities/*/api/dto.ts`, 페이지 `_api/dto.ts` | `api/` 폴더 안의 코드만 | 백엔드 계약 변경 |
| **도메인 모델** | `entities/*/model.ts` | 모든 계층 | 프론트 요구사항 변경 |
| **UI 전용 타입** | 컴포넌트 파일 안 (props, 화면 표시용 계산값) | 그 컴포넌트 | 화면 변경 |
| **폼 타입** | `z.infer<typeof schema>` (폼 옆) | 그 폼 | 입력 항목 변경 |
| **범용 타입** | `shared/` (`LatLng`, `Paginated<T>`, `ApiError`) | 모든 계층 | 거의 바뀌지 않음 |

**mapper가 변환 계층입니다.** 백엔드 필드명·형식이 바뀌면 `dto.ts`와 `mapper.ts`만 수정합니다.

```ts
// entities/feed/api/dto.ts — 백엔드 응답 그대로
export interface FeedResponseDto { feedId: number; likeCnt: number; createdAt: string; /* ... */ }

// entities/feed/api/mapper.ts
export const toFeed = (dto: FeedResponseDto): Feed => ({
  id: String(dto.feedId),
  likeCount: dto.likeCnt,
  createdAt: dto.createdAt,
  /* ... */
});

// entities/feed/api/feedApi.ts — 밖으로는 도메인 모델만 반환
export async function getFeed(id: Id): Promise<Feed> {
  return toFeed(await http.get<FeedResponseDto>(`/feeds/${id}`));
}
```

백엔드가 OpenAPI 문서를 제공하면 `openapi-typescript`로 DTO 타입을 생성해서 `dto.ts`가 생성된 타입을 다시 export하게 바꿉니다. 그때도 mapper 밖으로는 영향이 가지 않습니다.

### 8.5 API 연동 구조

```
[브라우저]  Client Component ─▶ entities/*/api ─▶ shared/api/http ─▶ /api/v1/*  ─(Next rewrites)─▶ Spring Boot (또는 mock 서버)
[서버]      page.tsx          ─▶ entities/*/api ─▶ shared/api/http ─▶ API_INTERNAL_URL (쿠키 전달) ─▶ Spring Boot (또는 mock 서버)
```

- **브라우저는 같은 도메인의 `/api/v1/*`로 요청**하고 `next.config.ts`의 rewrites가 백엔드로 전달합니다. CORS 설정이 필요 없고 httpOnly 쿠키를 그대로 쓸 수 있습니다.
- **서버는 백엔드 주소로 직접 요청**하고, 들어온 요청의 쿠키를 함께 전달합니다.
- `shared/api/http.ts`가 이 차이를 숨깁니다. `entities/*/api`는 어느 쪽에서 실행되는지 신경 쓰지 않습니다.
- 백엔드 오류 응답은 `ApiError(status, code, message)`로 바꿔서 던집니다.
  - 페이지(Server): 404 → `notFound()`, 401 → `redirect('/login')`, 그 외 → `error.tsx`
  - Client: `http.ts`가 401을 받으면 `/auth/refresh`를 한 번 보내고(동시에 받은 401은 재발급 하나를 함께 기다리고, 여러 탭 사이는 Web Locks로 한 번에 한 탭만 재발급) 다시 요청합니다. 재발급도 실패하면 `SESSION_EXPIRED`를 던지고, QueryClient 전역 설정이 `/login?error=SESSION_EXPIRED`로 보냅니다. `/auth/*` 경로의 401은 재발급 없이 그대로 돌려줍니다. 나머지 오류는 컴포넌트·토스트에서 처리

### 8.6 Mock → 실제 API 전환

- `mocks/`의 핸들러가 **DTO 형태의 응답**을 돌려줍니다. 따라서 mapper까지 실제와 같은 경로로 실행됩니다.
- 개발 시 mock 서버(localhost:4000)를 띄우고, rewrites 대상과 `API_INTERNAL_URL`을 mock 서버로 지정합니다.
- 백엔드에 일부 API만 있는 동안은 `BACKEND_URL`을 정합니다. `next.config.ts`의 `BACKEND_PATHS`에 있는 경로만 백엔드로 가고 나머지는 mock으로 갑니다. 백엔드에 API가 생기면 이 목록에 경로를 추가합니다. 장소 제보 화면은 관리자 로그인이 없어 mock에 둡니다. 목록은 배열입니다. `GET /api/v1/places`도 백엔드 응답에 지도 화면이 쓰는 필드가 없어 mock에 둡니다.
- 쓰기 요청(POST · PUT · PATCH · DELETE)은 `http.ts`가 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. 403 `INVALID_CSRF_TOKEN`이면 쿠키를 다시 읽어 한 번만 재시도합니다.
- **전환은 환경변수만 바꿉니다.** API 코드와 컴포넌트는 수정하지 않습니다. 기존 `USE_MOCK` 분기는 없앱니다.
- 같은 핸들러를 Vitest 컴포넌트 테스트에서도 재사용합니다.

| 환경변수 | 용도 |
| --- | --- |
| `API_BASE_URL` | 브라우저의 `/api/*`를 전달할 기본 주소. 개발은 mock, 배포는 백엔드 |
| `BACKEND_URL` | 개발 전용. 정하면 `BACKEND_PATHS`의 경로만 이 백엔드로 보냄 |
| `API_INTERNAL_URL` | 서버 → 백엔드(또는 mock) 주소. 서버 전용 |
| `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID` | 네이버 지도 Client ID (`ncpKeyId`, Web 서비스 URL·Dynamic Map 설정) |
| `NEXT_PUBLIC_NAVER_MAP_STYLE_ID` | (선택) Map Style Editor My Style ID. 설정 시 GL + 커스텀 스타일 |
| `NEXT_PUBLIC_NAVER_MAP_GL` | (선택) `true`면 Style ID 없이 GL 벡터맵만 사용 |

장소 검색처럼 **카카오 REST 키가 필요한 호출은 브라우저에서 직접 하지 않고** 백엔드를 거칩니다 (`GET /api/places/search`). 백엔드 준비 전에는 mock 서버가 대신 응답합니다.

### 8.7 지도 SDK 경계

- 페이지와 feature는 `@/shared/map`의 `Map`, `MapMarker`, `MarkerCluster`와 자체 타입(`LatLng`, `Bounds`)만 사용합니다.
- `naver.maps.*`는 `shared/map/naver/` 밖에서 쓰지 않습니다 (lint로 강제).
- **지도 제공자 인터페이스(`MapProvider` 등)는 만들지 않습니다.** 구현이 하나뿐인 인터페이스는 형식적인 추상화입니다. 컴포넌트 props가 이미 경계 역할을 합니다.
- 제공자를 바꾸게 되면 `shared/map/kakao/` 같은 폴더를 추가하고 `Map.tsx`가 그것을 쓰도록 바꿉니다. 페이지는 수정하지 않습니다.
- 마커 안의 카테고리 핀 모양은 도메인 정보이므로 `entities/place/ui/PlacePin`이 담당하고, `MapMarker`의 children으로 넘깁니다.

---

## 9. 실제 파일 배치 예시

### 9.1 피드

```
app/(main)/feed/
├── page.tsx              # Server: 첫 페이지를 postQueries.feed()로 prefetch → HydrationBoundary
├── loading.tsx           # FeedCard 스켈레톤 3개
└── _components/
    ├── FeedList.tsx      # 'use client' useInfiniteQuery(feedQueries.feed()), 화면 끝에서 다음 페이지
    └── FeedEmpty.tsx     # Server: 팔로우한 사람이 없을 때 탐색으로 안내

widgets/feed-card/FeedCard.tsx      # 'use client' FeedCardView + LikeButton + SavePlaceButton
features/like-feed/                 # useMutation + 낙관적 업데이트, feedKeys 무효화
features/save-place/                # 컬렉션 선택 시트, savedPlaceKeys·collectionKeys 무효화
entities/feed/api/queries.ts        # feedQueries.feed() — 피드·프로필·장소 상세가 같은 key 체계 사용
entities/feed/ui/FeedCardView.tsx   # 표시 전용, actions slot
```

**흐름:** `page.tsx`(Server)가 첫 페이지를 가져와서 HTML을 바로 그림 → `FeedList`(Client)가 같은 query key로 이어받아 다음 페이지를 불러옴 → 각 카드는 `widgets/feed-card`.

| 수정 상황 | 바뀌는 파일 |
| --- | --- |
| 카드에 공유 버튼 추가 | `widgets/feed-card/FeedCard.tsx` 하나 (피드·프로필·장소 상세·탐색에 모두 반영) |
| 피드를 페이지 번호 방식으로 변경 | `feed/_components/FeedList.tsx` 하나 |
| 백엔드가 `likeCnt` → `likeCount`로 변경 | `entities/feed/api/dto.ts`, `mapper.ts` |
| 좋아요 실패 시 처리 변경 | `features/like-feed/useLikeFeed.ts` |

### 9.2 지도

```
app/(main)/map/
├── page.tsx                    # Server: searchParams(category, status) 읽기 → 내 저장 장소 prefetch
├── loading.tsx
├── _components/
│   ├── MyMap.tsx               # 'use client' Map + MarkerCluster + PlacePin. 선택된 장소는 useState
│   ├── MapFilterBar.tsx        # 'use client' 필터를 URL searchParams로 변경
│   ├── MapSearch.tsx           # 'use client' place-search + save-place를 이 페이지에서 조합
│   └── PlaceSummarySheet.tsx   # BottomSheet + PlaceCard + SavePlaceButton
└── _lib/
    ├── filterSavedPlaces.ts    # 카테고리·방문 상태 필터 (순수 함수)
    └── filterSavedPlaces.test.ts

shared/map/
├── index.ts                    # Map, MapMarker, MarkerCluster, LatLng, Bounds만 export
├── types.ts
├── Map.tsx                     # 'use client' 공개 컴포넌트 → NaverMapView 연결
├── MapMarker.tsx
├── MarkerCluster.tsx
└── naver/
    ├── loadNaverSdk.ts         # SDK 스크립트 1회 로딩
    ├── NaverMapView.tsx        # 실제 네이버 지도 구현
    └── convert.ts              # LatLng ↔ naver.maps.LatLng
```

**흐름:** 필터는 URL에 있으므로 서버가 처음부터 필터된 결과를 그릴 수 있고, 링크를 공유해도 같은 화면이 열립니다. 1차에는 저장 장소 수가 적으므로 전부 불러오고, 많아지면 `savedPlaceQueries.inBounds(bounds)`(지도 화면 범위 조회, 기획서 8.1)로 바꿉니다. 이때 바뀌는 곳은 `MyMap.tsx`와 `entities/place/api`뿐입니다.

| 수정 상황 | 바뀌는 파일 |
| --- | --- |
| 지도 제공자 교체 | `shared/map/` 내부만 |
| 핀 디자인 변경 | `entities/place/ui/PlacePin.tsx`, `categories.ts` (지도를 쓰는 모든 화면에 반영) |
| 필터 항목 추가 | `MapFilterBar.tsx`, `filterSavedPlaces.ts` |

### 9.3 일정 편집

> 처음 설계(시간표 계산 · 체류 시간 · 이동 시간 · 영업시간 점검)는 사용하기 답답하다는 피드백으로 걷어 냈습니다.
> 지금은 **순서만 정하고, 시간이 정해진 곳(영화 · 식당 예약 등)에만 고정 시간을 적는** 단순한 편집입니다.

```
app/(main)/(tabs)/plans/[planId]/
├── page.tsx
├── _components/
│   ├── PlanEditor.tsx          # 상태별 화면: 계획 중(편집) · 계획 완료(보기) · 다녀옴
│   ├── PlanList.tsx            # 드래그 앤 드롭 목록 (드래그 라이브러리는 여기서만 사용), 장소 사이 이동 어림값
│   ├── PlanMap.tsx             # 순서대로 번호 핀 + 경로 선
│   └── sheets.tsx              # 장소 눌렀을 때 고정 시간 설정 · 일정에서 빼기, 장소 추가(저장한 곳 + 검색)
└── _hooks/
    └── usePlanEditor.ts        # 화면에 먼저 반영하고 뒤에서 저장 (낙관적 업데이트)

entities/plan/model/            # React import 금지. 입력 → 출력만 있는 순수 함수
├── types.ts        Plan { status: 'PLANNING' | 'UPCOMING' | 'DONE', items: PlanItem[] }
│                   PlanItem { place, fixedTime: 'HH:mm' | null, visited }
├── reorder.ts      moveItem · removeItem · setFixedTime · appendItem
│                   fixedTimeOrderIssue: 고정 시간이 목록 순서와 어긋난 곳 (막지 않고 안내만)
├── share.ts        planShareText: 공유할 일정 문구 (일정은 본인만 열 수 있어 링크 대신 글)
└── *.test.ts
```

**흐름:**
1. 일정 만들기: 날짜 · 지역 → 갈 곳 고르기(저장한 곳 + 검색). 새 일정은 ‘계획 중’으로 만들어집니다.
2. 계획 중: 핸들을 끌어 순서를 바꾸고, 장소를 누르면 고정 시간을 적거나 일정에서 뺍니다. 다 정하면 ‘계획 완료’.
3. 계획 완료: 보기 전용. ‘계획 수정’으로 다시 계획 중이 되고, ‘방문 기록하기’로 이어집니다.
4. 방문 기록: 다녀온 곳 체크(전체 선택 가능) + 일정에 없었지만 들른 곳 추가 → 일정이 ‘다녀옴’이 되고 내 지도에서 ‘방문 완료’.
5. 내 프로필 일정 탭의 예정 카드에는 공유 버튼과 ⋯ 메뉴(수정하기 · 삭제하기)가 있습니다.

| API | 내용 |
| --- | --- |
| `POST /api/plans` | `{ title, date, region, memo, items: [{ placeId, fixedTime }] }` → 계획 중 |
| `PUT /api/plans/:id` | `{ items }` 순서 · 고정 시간 저장, `{ status: 'UPCOMING' \| 'PLANNING' }` 계획 완료 · 다시 수정 |
| `DELETE /api/plans/:id` | 삭제 |
| `POST /api/plans/:id/visits` | `{ visitedItemIds, extraPlaceIds }` 방문 기록 + 추가로 들른 곳 |

**테스트:** `entities/plan/model/*.test.ts` (순서 변경 · 고정 시간 순서 안내 · 공유 문구).

---

## 10. 팀 개발자가 지켜야 할 아키텍처 규칙

### 배치
1. **새 코드는 가장 좁은 범위에서 시작합니다.** 페이지 `_components` → 공통 부모 → `features`/`widgets`/`entities` → `shared` 순서로, 두 번째 사용처가 실제로 생겼을 때만 올립니다.
2. **빈 계층 폴더나 빈 슬라이스를 미리 만들지 않습니다.**
3. **페이지 폴더 하나로 그 화면의 대부분을 찾을 수 있어야 합니다.** 화면을 고치려고 다섯 군데 이상을 열어야 한다면 배치를 다시 검토합니다.

### 의존
4. **아래 계층만 import합니다** (`app → widgets → features → entities → shared`).
5. **feature는 다른 feature를 import하지 않습니다.** 조합은 widget이나 페이지에서 합니다.
6. **슬라이스 밖에서는 `index.ts`로만 import합니다.** 내부 경로 import는 lint 오류입니다.
7. **형제 라우트의 `_` 폴더를 import하지 않습니다.** 공통 부모로 옮깁니다.

### Next.js
8. **`page.tsx`와 `layout.tsx`에 `'use client'`를 붙이지 않습니다.** Client Component는 말단에 둡니다.
9. **서버 전용 코드는 `*.server.ts` + `import 'server-only'`.** index에서 다시 export하지 않습니다.
10. **데이터를 가져오는 라우트에는 `loading.tsx`, 동적 라우트에는 `not-found.tsx`를 둡니다.**

### 데이터 · 상태
11. **컴포넌트는 `fetch`를 직접 호출하지 않습니다.** `entities/*/api` 또는 페이지 `_api`를 사용합니다.
12. **DTO는 `api/` 폴더 밖으로 나가지 않습니다.** 컴포넌트는 도메인 모델만 받습니다.
13. **TanStack Query는 8.3의 경우에만 씁니다.** 서버에서 한 번 그리면 끝나는 데이터는 props로 넘깁니다.
14. **Zustand store를 새로 만들려면 PR 설명에 8.2의 세 조건을 적습니다.**
15. **필터·탭·검색어처럼 공유 가능한 화면 상태는 URL에 둡니다.**

### 로직 · 외부 의존
16. **계산·검증 로직은 React를 import하지 않는 순수 함수로 작성하고, 바로 옆에 테스트를 둡니다.**
17. **단순 UI에는 UseCase·Port·Adapter 같은 추상화를 만들지 않습니다.** 인터페이스는 구현이 둘 이상일 때 만듭니다.
18. **`naver.maps`는 `shared/map/naver/` 밖에서 쓰지 않습니다.**

### 이름 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 슬라이스 폴더 | kebab-case | `save-place`, `feed-card` |
| 컴포넌트 파일 | PascalCase | `SavePlaceButton.tsx` |
| 그 외 파일 | camelCase | `businessHours.ts`, `useSavePlace.ts` |
| 테스트 | 대상 파일 옆 `*.test.ts(x)` | `timeline.test.ts` |
| 서버 전용 | `*.server.ts` | `session.server.ts` |

### 5명 협업

담당 영역을 나누면 각자 자기 라우트 폴더와 엔티티 안에서 대부분의 작업이 끝납니다.

| 담당 | 영역 |
| --- | --- |
| A — 피드·게시물 | `feed/`, `posts/`, `widgets/feed-card`, `features/like-feed`, `entities/feed` |
| B — 지도·장소·컬렉션 | `map/`, `places/`, `collections/`, `features/save-place`, `features/place-search`, `entities/place`, `entities/collection`, `shared/map` |
| C — AI 링크 가져오기 | `import/` |
| D — 일정 | `plans/`, `entities/plan` |
| E — 공통·인증·프로필 | `(auth)/`, `(main)/_components`, `users/`, `settings/`, `explore/`, `features/follow-user`, `entities/user`, `shared/` |

- 여러 사람이 함께 쓰는 `entities/`, `widgets/`, `features/`, `shared/`는 `CODEOWNERS`로 담당자 리뷰를 필수로 합니다.
- 계층 간 이동(승격·강등)은 기능 변경과 분리된 PR로 올립니다.

### 이 구조가 7가지 질문에 답하는 방식

| 질문 | 답 |
| --- | --- |
| 페이지 관련 파일을 쉽게 찾는가? | 페이지 전용 코드는 그 라우트 폴더 안에 있음. 밖에 있는 것은 `index.ts` import로 출처가 드러남 |
| 한 기능 수정이 다른 기능에 영향을 덜 주는가? | feature 간 import 금지, DTO 변경은 mapper에서 멈춤, 지도 SDK는 폴더 하나 |
| 5명이 동시에 개발하기 편한가? | 라우트·엔티티 단위로 담당이 나뉘고, 공유 영역은 CODEOWNERS로 관리 |
| 기능이 커져도 구조가 무너지지 않는가? | 승격 사다리라는 하나의 규칙으로 새 코드 위치가 정해짐. 계층은 필요할 때만 늘어남 |
| 공통화 때문에 결합도가 높아지지 않는가? | 두 번째 사용처가 실제로 생겨야 승격. 한 곳에서만 쓰이면 다시 내림 |
| 새 개발자가 이해하기 쉬운가? | 계층 5개, 규칙 18개. 대부분은 lint가 알려줌 |
| 테스트하기 쉬운가? | 핵심 규칙은 순수 함수, API는 MSW 핸들러 하나로 개발과 테스트에서 함께 사용 |

---

## 부록 B. 라우트 맵

| 경로 | 화면 | 접근 | 주요 렌더링 | 단계 |
| --- | --- | --- | --- | --- |
| `/` | 시작 화면 (로그인 시 `/feed`로) | 공개 | Server | 껍데기 |
| `/login`, `/signup` | 로그인 · 회원가입 통합(카카오 · Google) · 최초 가입 온보딩(닉네임 = ID → 프로필 사진 · 이름(선택) → 약관). 온보딩은 서버의 임시 가입 세션이 기준이라 새로고침 · 재접속에도 상태가 유지되고, 세션이 없으면 `/login`, 이미 가입했으면 `/feed`로 | 공개 | Server + 폼(Client) | 껍데기 |
| `/feed` | 팔로잉 피드 | 로그인 | Server prefetch + 무한 스크롤(Client) | 껍데기 |
| `/explore` | 탐색 | 공개 | Server + 버튼(Client) | 껍데기 |
| `/map` | 나만의 지도 | 로그인 | Server prefetch + 지도(Client) | 껍데기 |
| `/posts/new` | 게시물 작성 | 로그인 | 폼(Client) | 껍데기 |
| `/posts/[postId]` | 게시물 상세 | 공개 | Server + 좋아요·댓글(Client) | 껍데기 |
| `/places/[placeId]` | 장소 상세 | 공개 | Server + 찜·지도(Client) | 껍데기 |
| `/places/custom/new` | 커스텀 장소 등록 | 로그인 | 폼 + 지도(Client) | 2차 |
| `/collections` | 내 컬렉션 목록 | 로그인 | Server | 껍데기 |
| `/collections/new` | 컬렉션 생성 | 로그인 | 폼(Client) | 껍데기 |
| `/collections/[collectionId]` | 컬렉션 상세 (공유 링크) | 공개 | Server + 지도·찜(Client) | 껍데기 |
| `/import` | AI 외부 링크 가져오기 | 로그인 | 단계별 흐름(Client) | 껍데기 |
| `/plans` | 일정 목록 → 내 프로필 일정 탭(`/users/[username]?tab=plans`)으로 이동 | 로그인 | Client 이동 | 껍데기 |
| `/plans/new` | 일정 생성 (날짜·제목 → 상세로 이동) | 로그인 | 폼(Client) | 껍데기 |
| `/plans/[planId]` | 일정 상세 · 편집 · 점검 | 로그인 | Server 조회 + 편집기(Client) | 껍데기 |
| `/users/[username]` | 프로필 | 공개 | Server + 팔로우(Client) | 껍데기 |
| `/users/[username]/followers`, `/following` | 팔로워 · 팔로잉 | 공개 | Server + 팔로우(Client) | 1차 |
| `/messages` | DM 대화 목록 | 로그인 | Client (주기적 갱신) | 껍데기 |
| `/messages/[conversationId]` | DM 대화방 | 로그인 | Client (주기적 갱신) | 껍데기 |
| `/notifications` | 알림 | 로그인 | Server | 3차 |
| `/settings`, `/settings/profile` | 설정 · 프로필 편집 (닉네임 = ID 변경 · 이름 · 소개 · 활동 지역) | 로그인 | 폼(Client) | 1차 |
| `/settings/login-methods` | 로그인 수단 관리: 카카오 · Google 연결 · 해제 | 로그인 | Client | 껍데기 |
| `/settings/agreements`, `/notifications`, `/devices`, `/withdraw` | 약관 및 동의 관리 · 푸시 알림 설정 · 디바이스 관리 · 회원 탈퇴 | 로그인 | Client | 껍데기 |
| `/terms/[type]` | 약관 전문 (`service` · `privacy` · `marketing`) | 공개 | Client | 껍데기 |
| `/terms/consent` | 바뀐 약관 재동의 (`?next=` 동의한 뒤 돌아갈 찜콩 안 경로, 없으면 피드). 로그인 직후 서버가 보내거나, 403 `TERMS_RECONSENT_REQUIRED`를 받으면 QueryClient가 보냄. 고정 경로라 `[type]`보다 먼저 맞음 | 로그인 | Client | 1차 |
| `/tags`, `/tags/[name]` | 내 태그 검색(`?q=`) · 태그별 장소 | 로그인 | Client | 껍데기 (보류 기능, mock만) |
| `/places/[placeId]/photos`, `/places/[placeId]/tags` | 저장한 장소의 개인 사진 추가 · 삭제 / 개인 태그 편집 | 로그인 | Client | 껍데기 |
| `/places/mine`, `/places/[placeId]/edit` | 직접 등록한 장소 목록 · 수정 · 삭제 | 로그인 | 폼(Client) | 껍데기 |
| `/creator`, `/creator/apply`, `/creator/account`, `/creator/earnings` | 크리에이터 홈(심사 결과) · 신청 · 정산 계좌 · 수익 내역 | 로그인 | Client | 껍데기 |
| `/checkout/[collectionId]` | 유료 컬렉션 카드 결제 (지금은 mock 결제창) | 로그인 | Client | 껍데기 |
| `/purchases`, `/purchases/[purchaseId]/refund` | 구매 내역 · 환불 신청 | 로그인 | Client | 껍데기 |
| `/messages/groups`, `/messages/groups/new`, `/messages/groups/[groupId]` | 그룹 채팅 목록 · 만들기 · 관리 (대화방 화면은 아직 없음) | 로그인 | Client | 껍데기 |
| `/admin/**` | 관리자: 신고 처리 · 사용자 제재 · 콘텐츠 관리 · 크리에이터 심사 · 환불 · 정산 | 관리자 계정 | Client | 껍데기 |

**하단 탭 (모바일 우선):** 홈(`/feed`) · 내 지도(`/map`, `/collections`) · ➕ · DM(`/messages`) · 프로필(`/users/[username]`, 일정 탭 포함).
➕는 게시물 올리기(`/posts/new`) · 장소 직접 추가(`/places/new`) · AI 링크로 가져오기(`/import`) 세 갈래로 분기합니다. 탐색은 홈의 ‘추천’ 탭과 통합 검색(`/search`)이 대신합니다.
하단 탭이 있는 화면은 `app/(main)/(tabs)`, 뒤로가기 중심의 세부 작업 화면은 `app/(main)/(sub)`에 둡니다 (URL에는 영향 없음).
관리자 화면은 회원 화면과 섞이지 않도록 `app/(admin)/admin`에 둡니다. 관리자 로그인 방식은 소셜 로그인 전환 이후 아직 정하지 않았습니다(회원과 분리된 아이디 · 비밀번호 로그인 화면 검토 중). mock에서는 `/admin`으로 바로 들어갈 수 있습니다. 신고 목록·기각 API는 백엔드에 있지만, 관리자 로그인이 없어 `BACKEND_PATHS`에 넣지 않고 mock을 유지합니다. mock의 숨김·제재 버튼은 그대로 두되, 백엔드는 그 액션을 501로 거절하므로 실제 API로 바꾸는 시점에는 그 버튼을 켜지 않습니다. mock과 서버 모두 선택한 신고만 닫고, 같은 대상의 다른 신고는 그대로 둡니다. 카드 제목은 스냅샷 첫 줄이고, 스냅샷이 없으면 현재 첫 줄입니다. 썸네일은 스냅샷 이미지만 쓰고, 링크는 공개(`LIVE`)일 때만 보여 줍니다. 첫 줄이 바뀌었으면 현재 글을 함께 보여 줍니다. `LIVE`가 아니면 배지를 붙입니다. 숨김은 경고색이고, 삭제됨과 확인할 수 없음은 중립색입니다. 이미 처리된 신고를 다시 닫으면 목록을 다시 불러오고 시트를 닫습니다.

---

## 부록 C. 엔티티 모델 초안

백엔드 DTO가 정해지기 전까지 프론트에서 쓸 **도메인 모델**입니다. 각 타입은 표시된 파일이 소유합니다. Spring Boot 응답이 달라도 mapper에서 맞추므로 이 모델은 프론트 요구사항이 바뀔 때만 수정합니다.

```ts
// shared/api/… , shared/map/types.ts, shared/config/visibility.ts
type Id = string;
type LatLng = { lat: number; lng: number };
type Visibility = 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE';

// entities/user/model.ts
interface User {
  id: Id;
  username: string;               // 닉네임 = 사용자 ID (영문, 중복 불가)
  nickname: string;               // 화면 표시 이름 = 이름 ?? username (서버 계산)
  name?: string | null;           // 사용자가 정한 이름 (중복 가능, 없으면 null)
  profileImageUrl?: string;
  bio?: string;
  followerCount: number;
  followingCount: number;
  isFollowing?: boolean;
}

// entities/place/model.ts
type PlaceCategory = 'CAFE' | 'RESTAURANT' | 'PHOTO_SPOT' | 'SHOP' | 'EXHIBITION' | 'STREET' | 'ATTRACTION' | 'ETC';

interface Place {
  id: Id;
  name: string;
  address?: string;
  location: LatLng;
  category: PlaceCategory;
  isCustom: boolean;              // 사용자 지정 좌표 장소
  externalPlaceId?: string;       // 카카오/구글 장소 ID
  businessHours?: BusinessHours;  // 없으면 "확인 필요"
}

type VisitStatus = 'WANT_TO_GO' | 'VISITED';

interface SavedPlace {
  id: Id;
  place: Place;
  collectionIds: Id[];
  visitStatus: VisitStatus;
  memo?: string;
  photoUrls: string[];
  source?: { type: 'POST' | 'COLLECTION' | 'LINK_IMPORT' | 'SEARCH'; refId?: Id; url?: string };
}

// entities/collection/model.ts
interface Collection {
  id: Id;
  owner: User;
  title: string;
  description?: string;
  coverImageUrl?: string;
  visibility: Visibility;
  pinColor?: string;
  pinIcon?: string;
  placeCount: number;
}

// entities/feed/model.ts
// 사진마다 장소를 하나씩 연결할 수 있습니다 (없을 수도 있음). 같은 장소가 여러 사진에 연결될 수 있습니다.
interface PostPhoto {
  url: string;
  placeId: Id | null;
}

interface Post {
  id: Id;
  author: User;
  photos: PostPhoto[];
  places: Place[];                // 사진에 연결된 장소 (중복 제거, 사진 순서)
  content: string;
  visibility: Visibility;
  isSponsored: boolean;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  createdAt: string;
}

// app/(main)/import/_api/ — 이 페이지에서만 사용하므로 entities가 아닌 페이지 옆에 둠
interface ImportCandidate {
  tempId: Id;
  extractedName: string;
  extractedAddress?: string;
  matchedPlace?: Place;
  confidence: number;             // 0~1
  needsReview: boolean;           // 일치도 낮음 → 사용자 확인 필요
  isDuplicate: boolean;           // 이미 저장한 장소
  sourceUrl: string;
}

// entities/plan/model/types.ts
// ※ 일정 단순화 반영: 아래 처음 설계 대신 실제 구현은
//   Plan { id, title, date, region, memo, status: 'PLANNING' | 'UPCOMING' | 'DONE', items: PlanItem[] }
//   PlanItem { id, place, fixedTime: string | null, visited } 입니다 (체류 시간 · 시간표 · 경고 없음).
//   최신 정의는 코드(types.ts)가 기준입니다.
interface Plan {
  id: Id;
  title: string;
  date: string;                   // YYYY-MM-DD
  dayStartTime: string;           // HH:mm, 시간표 계산 시작 시각
  stops: PlanStop[];
  status: 'UPCOMING' | 'DONE';
}

interface PlanStop {
  id: Id;
  place: Place;
  order: number;
  fixedStartTime?: string;        // HH:mm, 예약 확정 시각
  stayMinutes: number;
  isFixed: boolean;               // 예약 확정 → 순서 변경·최적화에서 고정
  visited: boolean;
}

interface TimelineEntry {          // buildTimeline 결과 (저장하지 않고 매번 계산)
  stopId: Id;
  arriveAt: string;               // HH:mm
  departAt: string;
  waitMinutes: number;            // 고정 일정 전 대기
  travelMinutesFromPrev?: number;
  travelIsEstimate: boolean;      // 직선거리 추정치 여부
}

type PlanWarning =
  | { type: 'CLOSED_DAY' }
  | { type: 'OUTSIDE_HOURS' }
  | { type: 'NOT_ENOUGH_TRAVEL_TIME'; neededMinutes: number }
  | { type: 'HOURS_UNKNOWN' };
```

---

## 부록 D. 기본 틀 단계 작업 순서 (제안)

1. Next.js 프로젝트 생성 (`frontend/`), TypeScript strict · ESLint(계층 규칙 포함) · `@/*` 경로 alias · Vitest 설정
2. `globals.css` 디자인 토큰 이름 정의, 폰트
3. `shared/ui` 기본 컴포넌트, `shared/api/http.ts`, `shared/config/env.ts`
4. `mocks/` 서버 + fixtures, `next.config.ts` rewrites
5. `app/` 라우트 생성 (빈 페이지 + `loading`/`not-found`), `(main)` 앱 셸, `proxy.ts` 접근 제어
6. `entities/*` 모델 · DTO · mapper · queries
7. 화면별 정적 UI: 피드 → 게시물 상세/작성 → 지도 → 컬렉션 → 외부 링크 가져오기 → 일정
8. `shared/map` 네이버 지도 연동
9. `entities/plan/model` 순수 함수 + 단위 테스트
