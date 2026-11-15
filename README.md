# 찜콩 (Pickong)

> 당신의 마침표. 누군가의 "따옴표"가 됩니다
> 취향과 경험이 쌓이는 소셜 지도

## 폴더

```
pickong/
├── BLUEPRINT.md    프론트엔드 설계 문서
├── frontend/       Next.js 앱 + 개발용 mock API 서버
└── backend/        Spring Boot REST API
```

## 실행

| 대상 | 명령 | 주소 |
| --- | --- | --- |
| 프론트 + mock API | `cd frontend && npm install && npm run dev` | http://localhost:3000 (mock: 4000) |
| 백엔드 | `cd backend && ./mvnw spring-boot:run` | http://localhost:8080 |

백엔드 API가 준비되기 전까지 프론트는 mock API로 동작합니다. 연결 방법은 [frontend/README.md](frontend/README.md)와 [backend/README.md](backend/README.md)에 있습니다.

## 필요한 도구

- Node.js 24 이상 (mock 서버가 TypeScript를 바로 실행합니다)
- JDK 21
