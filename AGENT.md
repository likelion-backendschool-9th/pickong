# AGENT.md

이 문서는 이 저장소에서 작업하는 AI 에이전트와 팀원이 공통으로 따르는 개발 규칙입니다.
코드 작성, 브랜치 생성, 커밋, PR 작성 시 아래 규칙을 반드시 따릅니다.

## 설계 문서: BLUEPRINT.md

프론트엔드(`frontend/`) 코드를 작성하거나 수정하기 전에 저장소 루트의 `BLUEPRINT.md`를 먼저 읽고, 그 내용을 따릅니다.
`BLUEPRINT.md`는 찜콩(Pickong) 프론트엔드의 기술 스택, 폴더 구조, 계층 의존 규칙, 상태 관리 및 API 연동 규칙, 라우트 맵, 엔티티 모델을 정의한 설계 문서입니다.

### 적용 범위와 우선순위

- 폴더 구조, 코드 배치, 의존 방향, Server/Client Component 구분, 상태 관리, API 연동 방식은 `BLUEPRINT.md`를 기준으로 합니다.
- 브랜치, 커밋, PR, 리뷰, 금지 사항은 이 문서의 1~10장을 따릅니다. 프론트엔드와 백엔드 모두에 적용됩니다.
- `backend/`(Spring Boot)는 `BLUEPRINT.md`의 범위 밖이므로 이 문서의 규칙을 따릅니다.
- `BLUEPRINT.md`와 실제 코드가 다를 때는 임의로 한쪽에 맞추지 않고 팀원에게 확인합니다. 구조나 규칙을 바꾸는 경우에는 `BLUEPRINT.md`도 같은 PR에서 함께 수정합니다.
- 다만 `BLUEPRINT.md` 부록 C의 `Plan` 관련 타입은 최신 정의가 코드(`types.ts`)에 있다고 명시되어 있으므로, 해당 부분은 코드를 기준으로 합니다.

### 프론트엔드 핵심 규칙 요약

자세한 내용과 예외는 `BLUEPRINT.md`를 확인합니다.

1. **계층 의존 방향:** `app → widgets → features → entities → shared` 순서로, 위 계층만 아래 계층을 import합니다.
2. **feature 간 import 금지:** 조합이 필요하면 widget이나 페이지(`app/`)에서 합니다.
3. **새 코드는 가장 좁은 범위에서 시작합니다.** 페이지 `_components` → 공통 부모 라우트 → `features`/`widgets`/`entities` → `shared` 순서로, 두 번째 사용처가 실제로 생겼을 때만 올립니다. 빈 계층 폴더나 슬라이스를 미리 만들지 않습니다.
4. **슬라이스 밖에서는 `index.ts`로만 import합니다.** (`widgets/*`, `features/*`, `entities/*`) 내부 경로 import는 금지합니다.
5. **형제 라우트의 `_` 폴더를 import하지 않습니다.** 공통 부모로 옮깁니다.
6. **`page.tsx`와 `layout.tsx`는 항상 Server Component**이며 `'use client'`를 붙이지 않습니다. Client Component는 트리의 말단에 둡니다.
7. **서버 전용 코드는 `*.server.ts` + `import 'server-only'`**로 작성하고 `index.ts`에서 다시 export하지 않습니다.
8. **데이터를 가져오는 라우트에는 `loading.tsx`, 동적 라우트에는 `not-found.tsx`를 둡니다.**
9. **컴포넌트에서 `fetch`를 직접 호출하지 않습니다.** `entities/*/api` 또는 페이지 `_api`를 사용합니다.
10. **DTO는 `api/` 폴더 밖으로 내보내지 않습니다.** 컴포넌트는 도메인 모델만 받고, 변환은 `mapper.ts`에서 합니다.
11. **TanStack Query는 클라이언트에서 다시 조회·변경되는 데이터에만** 사용합니다. 변경(mutation)도 TanStack Query로 통일하며, Server Actions는 사용하지 않습니다.
12. **Zustand store는 초기에 만들지 않습니다.** 필요하면 `BLUEPRINT.md` 8.2의 세 조건을 PR 설명에 적습니다.
13. **필터, 탭, 검색어처럼 공유 가능한 화면 상태는 URL(`searchParams`)에 둡니다.**
14. **계산·검증 로직은 React를 import하지 않는 순수 함수로 작성하고, 바로 옆에 테스트를 둡니다.**
15. **단순 UI에는 UseCase, Port, Adapter 같은 추상화를 만들지 않습니다.**
16. **`kakao.maps`는 `shared/map/kakao/` 밖에서 사용하지 않습니다.**
17. **색상은 `app/globals.css`의 `@theme` 토큰 이름으로만 사용하고, hex 값을 컴포넌트에 직접 쓰지 않습니다.**
18. **Mock 분기(`USE_MOCK`)를 코드에 넣지 않습니다.** mock과 실제 API 전환은 환경변수로만 합니다.

### 이름 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 슬라이스 폴더 | kebab-case | `save-place`, `post-card` |
| 컴포넌트 파일 | PascalCase | `SavePlaceButton.tsx` |
| 그 외 파일 | camelCase | `businessHours.ts`, `useSavePlace.ts` |
| 테스트 | 대상 파일 옆 `*.test.ts(x)` | `timeline.test.ts` |
| 서버 전용 | `*.server.ts` | `session.server.ts` |

### 작업 전 확인

- 새 파일을 만들기 전에 `BLUEPRINT.md` 6장의 승격 사다리와 판단 순서로 위치를 정합니다.
- 새 라우트를 추가할 때는 `BLUEPRINT.md` 부록 B의 라우트 맵(접근 권한, 렌더링 방식)을 확인합니다.
- 엔티티 모델을 만들거나 수정할 때는 `BLUEPRINT.md` 부록 C를 확인합니다.
- 프론트엔드 변경은 ESLint(계층 의존 규칙 포함)와 테스트를 통과해야 PR을 올릴 수 있습니다.

## 요약

- PR 전 `./gradlew build`(테스트 포함)를 통과시킵니다.
- 작업 종류별 브랜치 prefix를 사용합니다. 예: `feature/`, `fix/`, `refactor/`, `test/`, `docs/`
- 브랜치 이름은 영어 소문자와 하이픈(`-`)만 사용합니다.
- 하나의 브랜치는 가급적 하나의 기능 또는 작업 단위로 생성합니다.
- 커밋은 의미 단위로 나누고, 변경 내용을 알아보기 쉽게 작성합니다.
- PR 전 `dev` 브랜치의 최신 변경 사항을 반영합니다.
- 기본적으로 1명 이상 승인 후 병합합니다.

---

## 1. 코드 컨벤션

| 영역 | 컨벤션 / 도구 |
| --- | --- |
| Java | Java 코딩 컨벤션 |
| 포맷터 | VS Code 코드 스타일 공유, `.editorconfig` |
| 정적 분석 (선택) | Checkstyle |
| DB 네이밍 | 테이블·컬럼 snake_case, 엔티티·필드 camelCase |
| 설정 파일 | `application.yml` (YAML 들여쓰기 2칸) |

- 가능하면 레포지토리 루트에 `.editorconfig`를 두고, IntelliJ의 저장 시 자동 포맷(Actions on Save)을 팀 전원이 켭니다.
- 엔티티를 컨트롤러에서 직접 반환하지 않고 DTO를 사용합니다.

---

## 2. 브랜치 네이밍 컨벤션

### 기본 형식

```
타입/작업명
타입/이슈번호-작업명   (GitHub Issue와 연결되는 경우)
```

### 브랜치 타입

| Prefix | 용도 | 예시 |
| --- | --- | --- |
| `feature/` | 새로운 기능 개발 | `feature/12-member-signup` |
| `fix/` | 버그 수정 | `fix/4-outbound-stock-check` |
| `refactor/` | 동작 변경 없는 코드 개선 | `refactor/223-product-service` |
| `docs/` | 문서 작성 및 수정 | `docs/readme` |
| `test/` | 테스트 코드 작성 및 수정 | `test/113-stock-service` |
| `chore/` | 기타 작업 및 설정 변경 | `chore/add-springdoc` |
| `hotfix/` | 운영 환경의 긴급 버그 수정 | `hotfix/payment-error` |
| `perf/` | 성능 개선 | `perf/menu-query` |
| `design/` | 디자인 개선 | `design/menu-page` |
| `ci/` | CI/CD 및 배포 설정 작업 | `ci/deploy-set` |

### 브랜치 작성 규칙

1. 영어 소문자를 사용합니다.
   - `feature/Member-Signup` (X) / `feature/member-signup` (O)
2. 단어 구분은 하이픈(`-`)을 사용합니다.
   - `feature/member_signup` (X) / `feature/member-signup` (O)
3. 작업 내용을 알아볼 수 있도록 작성합니다.
   - `feature/update` (X) / `feature/member-signup` (O)
4. 하나의 브랜치는 가급적 하나의 기능 또는 작업 단위로 생성합니다.
   - 회원가입과 게시물 조회 등 관련 없는 기능을 하나의 브랜치에 섞지 않습니다.
5. GitHub Issue가 있는 경우 이슈 번호를 포함합니다.
   - `feature/12-member-signup`, `fix/24-login-error`, `refactor/35-order-service`

### 브랜치 네이밍 예시

```
feature/12-member-signup
feature/18-post-create
feature/25-place-bookmark

fix/31-login-error
fix/42-invalid-token

refactor/53-order-service

test/61-stock-service

docs/72-api-document

chore/add-springdoc
hotfix/payment-error
perf/menu-query
design/profile-page
ci/deploy-set
```

---

## 3. PR 흐름

작업 브랜치 생성 → 기능 개발 → 테스트 → PR 생성 → 리뷰 → 승인 → `dev` 병합

### 1) `dev` 브랜치를 최신 상태로 가져옵니다.

```bash
git checkout dev
git pull origin dev
```

### 2) 작업 브랜치를 생성합니다.

```bash
git checkout -b feature/12-member-signup
```

### 3) 기능 개발 및 테스트를 진행합니다.

작업을 완료한 뒤 빌드와 테스트를 통과시킵니다.

```bash
./gradlew build
```

### 4) 변경 사항을 커밋하고 푸시합니다.

```bash
git add .
git commit -m "feature: 회원 가입 API 추가"
git push origin feature/12-member-signup
```

`git add .`를 실행하기 전에 민감한 설정 파일이나 불필요한 파일이 포함되지 않았는지 확인합니다.

### 5) PR을 생성하고 본문을 작성합니다.

PR에는 변경 내용, 관련 요구사항, 테스트 결과, DB/API 영향을 작성합니다.

### 6) 리뷰를 받고 필요한 내용을 수정합니다.

- 리뷰 의견을 확인합니다.
- 수정이 필요한 부분은 반영합니다.
- 충돌이 발생하면 관련 팀원과 함께 해결합니다.
- 병합 전에 `dev`의 최신 변경 사항을 반영합니다.

### 7) 승인 후 `dev`에 병합합니다.

기본적으로 1명 이상의 승인을 받은 뒤 병합합니다.

---

## 4. 커밋 메시지 컨벤션

### 기본 형식

```
타입: 작업 내용
```

영어 타입과 한국어 작업 내용을 조합합니다.

### 커밋 타입

| 타입 | 설명 | 예시 |
| --- | --- | --- |
| `feature` | 새로운 기능 추가 | `feature: 좌석 예약 기능 구현` |
| `fix` | 버그 수정 | `fix: 로그인 오류 수정` |
| `refactor` | 기능 변경 없는 코드 개선 | `refactor: 주문 서비스 로직 분리` |
| `docs` | 문서 작성 및 수정 | `docs: README 수정` |
| `style` | 코드 포맷팅, 세미콜론 등 동작에 영향 없는 수정 | `style: 코드 들여쓰기 수정` |
| `test` | 테스트 코드 추가 및 수정 | `test: 주문 서비스 단위 테스트 추가` |
| `chore` | 기타 유지보수 작업 | `chore: 불필요한 파일 삭제` |
| `perf` | 성능 개선 | `perf: 메뉴 조회 쿼리 최적화` |
| `build` | 빌드 및 의존성 설정 | `build: Gradle 의존성 추가` |
| `ci` | CI/CD 설정 및 수정 | `ci: GitHub Actions 배포 설정` |
| `revert` | 이전 커밋 되돌리기 | `revert: 좌석 예약 기능 변경 취소` |
| `design` | UI 및 디자인 변경 | `design: 마이페이지 디자인 변경` |

### 커밋 메시지 작성 형식

```
type: 제목

본문 (선택)

Refs: REQ-○○○ (선택)
```

- 제목에는 변경 내용을 간결하게 작성합니다.
- 본문에는 무엇을, 왜 변경했는지 설명합니다.
- 관련 요구사항 ID가 있다면 `Refs:`를 작성할 수 있습니다.
- 본문과 `Refs:`는 필요한 경우에만 작성합니다.

### 커밋 메시지 예시

**새로운 기능 추가**

```
feature: 회원 가입 API 추가

이메일 중복 여부를 확인한 뒤 비밀번호를 BCrypt로 암호화해
회원을 저장하는 POST /api/members를 구현했다.

Refs: REQ-001
```

**버그 수정**

```
fix: 출고 시 재고가 음수로 저장되는 오류 수정

출고 수량이 현재 재고보다 클 때 OUT_OF_STOCK 예외가 발생하도록
Product.decreaseStock()에 검증 로직을 추가했다.

Refs: REQ-005
```

**리팩터링**

```
refactor: 상품 조회 응답을 DTO로 분리

엔티티를 직접 반환하던 ProductController를
ProductResponse DTO를 반환하도록 변경했다.
```

**테스트**

```
test: 입출고 서비스 단위 테스트 추가

재고 부족, 상품 없음 상황에서 예외가 발생하는지
Mockito로 검증하는 StockServiceTest를 작성했다.
```

**문서**

```
docs: 시스템 설계 명세 페이지 업데이트

입출고 API의 URI와 응답 형식을 최신 코드에 맞게 수정했다.
```

**성능 개선**

```
perf: 장소 조회 쿼리 최적화

조회 조건과 실행 계획을 분석하고 인덱스를 추가하여
장소 목록 조회 성능을 개선했다.
```

**빌드 설정**

```
build: Gradle 의존성 추가

테스트에 필요한 라이브러리 의존성을 추가했다.
```

**CI/CD**

```
ci: GitHub Actions 배포 워크플로 수정

빌드 및 테스트 완료 후 배포가 진행되도록
워크플로 실행 순서를 수정했다.
```

**디자인**

```
design: 마이페이지 디자인 변경

프로필 정보와 장소 컬렉션 영역의 레이아웃을 수정했다.
```

**이전 변경 되돌리기**

```
revert: 좌석 예약 기능 변경 취소

예약 기능 변경으로 발생한 문제를 해결하기 위해
이전 변경 사항을 되돌렸다.
```

---

## 5. 커밋 작성 팁

- 한 커밋에는 한 가지 의미 있는 변경만 담습니다.
- 제목만 봐도 어떤 기능이나 클래스를 바꿨는지 알 수 있게 작성합니다.
- 가능하면 한국어로 작성합니다.
- 명령문 형태로 작성합니다.
  - `회원 가입 API 추가`
  - `재고 검증 로직 수정`
  - `상품 응답 DTO 분리`
- 작업 내용을 모호하게 작성하지 않습니다.
  - `feature: 수정` (X) / `feature: 장소 저장 API 구현` (O)
- 관련 요구사항이 있으면 본문 끝에 `Refs: REQ-○○○`를 붙일 수 있습니다.
- 비밀번호, 토큰, `application-secret.yml`, `.env` 파일은 절대 커밋하지 않습니다.
- 빌드 결과물(`build/`, `out/`)과 IDE 설정(`.idea/`)은 `.gitignore`에 등록합니다.
- **AI가 커밋을 작성할 때 contributor(공동 작성자)를 포함하지 않습니다.**
  - 커밋 메시지에 `Co-authored-by:`, `Co-Authored-By:` 등의 trailer를 추가하지 않습니다. (예: `Co-Authored-By: Claude <noreply@anthropic.com>`)
  - "Generated with ...", "Created by AI" 같은 AI 생성 표시 문구도 커밋 메시지와 PR 본문에 넣지 않습니다.
  - 커밋의 author는 사용자의 Git 설정(`user.name`, `user.email`)을 그대로 따르며, AI 에이전트의 이름이나 이메일로 변경하지 않습니다.
  - 커밋 메시지는 4장의 형식(`타입: 작업 내용`, 본문과 `Refs:`는 선택)만 따릅니다.

### 커밋 분리 기준

관련 변경끼리는 하나의 커밋으로 묶되, 독립적으로 설명할 수 있는 작업은 별도 커밋으로 나누는 것을 권장합니다.

```
feature: 장소 저장 API 구현
test: 장소 저장 서비스 테스트 추가
docs: 장소 저장 API 명세 업데이트
```

단, 지나치게 작은 단위로 커밋을 나눌 필요는 없습니다.

---

## 6. PR 본문 가이드

PR 생성 시 다음 템플릿을 사용합니다.

```markdown
## 관련 요구사항 (선택)
- REQ-○○○

## 변경 내용
(무엇을, 왜 바꿨는지 작성)

## 주요 변경 파일
- 추가:
- 변경:
- 삭제:

## 테스트 / 확인 결과
- [ ] ./gradlew build 통과 (테스트 포함)
- [ ] 단위 테스트 추가 또는 수정
- [ ] Postman 또는 Swagger로 API 호출 확인
- [ ] 예외 상황(잘못된 입력, 권한 없음 등) 응답 확인

## DB / API 영향
- 엔티티·테이블 변경, API URI·요청·응답 형식 변경이 있다면 명시
- 없으면 "없음" 작성

## 셀프 체크
- [ ] 코드 컨벤션·포맷 적용
- [ ] 비밀번호·키·토큰이 코드와 설정 파일에 포함되지 않음
- [ ] 엔티티를 직접 반환하지 않고 DTO 사용
- [ ] 시스템 설계 명세(요구사항·ERD·API) 페이지 업데이트 완료
- [ ] API 변경 시 팀원에게 공유 완료
```

---

## 7. PR 리뷰 규칙

### 기본 리뷰 규칙

- 가능하면 모든 팀원이 PR을 확인합니다.
- 기본적으로 1명 이상 승인 후 병합합니다.
- 일정이 촉박한 경우 일반 문서 수정이나 단순 오타 수정은 바로 병합할 수 있습니다.
- 단, 아래 항목은 예외 없이 리뷰가 필요합니다.

### 1인 이상 리뷰 필수 항목

- 엔티티 구조·연관관계 변경 (DB 스키마에 영향)
- 공통 모듈 변경 (`global` 패키지: 공통 응답, 예외 처리, 설정)
- 인증·인가 로직 변경 (Spring Security 설정, 필터, 권한 체크)
- 비밀번호 암호화, 토큰 발급·검증 관련 변경
- API URI 또는 요청·응답 형식 변경 (다른 팀원 코드에 영향)
- `build.gradle` 의존성 추가·버전 변경
- `application.yml` 등 공통 설정 변경

### 리뷰 시 확인할 내용

- 변경된 기능이 요구사항에 맞게 구현되었는가?
- 기존 기능에 영향을 줄 가능성이 있는가?
- 예외 상황이 적절히 처리되는가?
- 불필요한 중복 코드가 없는가?
- 테스트가 충분한가?
- 다른 팀원의 작업에 영향을 주는 변경이 있는가?

---

## 8. 병합 전 최종 확인

PR을 병합하기 전 아래 내용을 확인합니다.

```bash
# dev 최신 내용 확인
git fetch origin

# 작업 브랜치에 dev 변경 사항 반영
git merge origin/dev

# 빌드 및 전체 테스트
./gradlew clean build

# 애플리케이션 실행 확인
./gradlew bootRun

# API 동작 확인 (Swagger UI 또는 Postman)
# http://localhost:8080/swagger-ui/index.html
```

### 최종 체크리스트

- [ ] `dev`의 최신 변경 사항 반영
- [ ] 빌드 성공
- [ ] 전체 테스트 통과
- [ ] API 정상 동작 확인
- [ ] 충돌 해결 완료
- [ ] DB/API 변경 사항 문서화
- [ ] 필수 리뷰 승인 완료

---

## 9. 금지 사항

- `main` 또는 `dev` 브랜치에 직접 push 금지
  - 기존 팀 규칙상 일정이 촉박하면 `dev` 강제 push 예외를 허용하되, 반드시 팀원과 사전 협의합니다.
- 빌드 실패·테스트 실패 상태로 병합 금지
- DB 비밀번호, JWT 시크릿 키, `application-secret.yml`, `.env` 커밋 금지
- 팀원 공유 없이 엔티티 구조, API URI, 공통 응답 형식 변경 금지
- 시스템 설계 명세 페이지와 실제 코드가 다르게 방치되는 것 금지
- AI가 작성한 커밋에 `Co-authored-by:` 등 contributor(공동 작성자) trailer나 AI 생성 표시 문구 포함 금지

---

## 10. 컨벤션 요약

### 브랜치

형식: `타입/이슈번호-작업명`

```
feature/12-member-signup
fix/24-login-error
refactor/35-order-service
test/42-place-service
docs/api-document
chore/add-springdoc
hotfix/payment-error
perf/menu-query
design/profile-page
ci/deploy-set
```

### 커밋

형식: `타입: 작업 내용`

```
feature: 회원 가입 API 추가
fix: 로그인 오류 수정
refactor: 주문 서비스 로직 분리
docs: README 수정
style: 코드 들여쓰기 수정
test: 주문 서비스 단위 테스트 추가
chore: 불필요한 파일 삭제
perf: 메뉴 조회 쿼리 최적화
build: Gradle 의존성 추가
ci: GitHub Actions 배포 설정
revert: 좌석 예약 기능 변경 취소
design: 마이페이지 디자인 변경
```

### PR

작업 브랜치 생성 → 기능 개발 → 테스트 → PR 생성 → 리뷰 → 승인 → `dev` 병합

팀원 모두가 동일한 규칙을 적용하여 일관성 있는 Git 이력을 유지하는 것을 목표로 합니다.
