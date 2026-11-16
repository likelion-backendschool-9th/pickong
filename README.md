# 찜콩 (Pickong)

> 당신의 마침표. 누군가의 "따옴표"가 됩니다
> 취향과 경험이 쌓이는 소셜 지도

## 폴더

```
pickong/
├── README.md                    저장소 안내 (이 문서)
├── AGENT.md                     공통 개발 규칙 (브랜치 · 커밋 · PR · 빌드)
├── BLUEPRINT.md                 프론트엔드 설계 문서
├── .gitignore                   공통 무시 규칙 (OS · IDE · 로그 · 환경변수)
├── .editorconfig                공통 에디터 설정 (인코딩 · 줄바꿈 · 들여쓰기)
│
├── frontend/                    Next.js (App Router) · TypeScript · Tailwind
│   ├── src/
│   │   ├── app/                 라우트와 페이지 전용 코드
│   │   │   ├── (main)/(tabs)/   하단 내비게이션 화면: 홈 · 내 지도 · 컬렉션 · DM · 프로필(일정 탭 포함)
│   │   │   ├── (main)/(sub)/    세부 작업 화면: 게시물 작성 · 장소 · 링크 가져오기 · 검색 · DM 대화방 · 설정 등
│   │   │   └── globals.css      디자인 토큰 (@theme)
│   │   ├── widgets/             여러 기능을 조합한 화면 블록 (post-card)
│   │   ├── features/            사용자 동작 단위 (save-place · like-post · bookmark-post ...)
│   │   ├── entities/            도메인 모델 · API · 표시 UI (user · place · post · collection · plan)
│   │   └── shared/              도메인을 모르는 공통 코드 (ui · map · api · lib · config)
│   ├── mocks/                   개발용 mock API 서버 (handlers: 응답 규칙, fixtures: 샘플 데이터)
│   ├── public/                  정적 파일 (예시 사진)
│   └── README.md                프론트 실행 · mock 전환 · 폴더 설명
│
└── backend/                     Spring Boot 4.1 · Java 21 · Gradle
    ├── src/main/java/com/pickong/
    ├── src/main/resources/      application.yml
    ├── src/test/java/com/pickong/
    ├── build.gradle             의존성 · 빌드 설정
    ├── settings.gradle          프로젝트 이름
    ├── gradlew                  Gradle Wrapper (Gradle 설치 없이 실행, Windows는 gradlew.bat)
    ├── gradle/wrapper/          Wrapper가 내려받을 Gradle 버전 (9.8.2)
    └── README.md                백엔드 실행 · 프론트와 맞출 API 계약
```

계층 규칙과 코드 배치 기준은 [BLUEPRINT.md](BLUEPRINT.md)에 있습니다.

## 실행

| 대상 | 명령 | 주소 |
| --- | --- | --- |
| 프론트 + mock API | `cd frontend && npm install && npm run dev` | http://localhost:3000 (mock: 4000) |
| 백엔드 | `cd backend && ./gradlew bootRun` | http://localhost:8080 |

백엔드 API가 준비되기 전까지 프론트는 mock API로 동작합니다. 연결 방법은 [frontend/README.md](frontend/README.md)와 [backend/README.md](backend/README.md)에 있습니다.

## 필요한 도구

- Node.js 24 이상 (mock 서버가 TypeScript를 바로 실행합니다)
- JDK 21
