/**
 * 개발용 mock API 서버 (localhost:4000).
 * 앱 번들에 포함되지 않으며, next.config.ts의 rewrites가 /api/*(BACKEND_URL을 정하면 백엔드에 있는 경로는 빼고)를 이 서버로 전달합니다.
 * 실제 백엔드에 연결하는 방법은 frontend/README.md를 봅니다.
 *
 * 실행: npm run mock (npm run dev에 포함)
 */
import { createServer } from "node:http";
import { accountRoutes } from "./handlers/account.ts";
import { adminRoutes } from "./handlers/admin.ts";
import { authRoutes } from "./handlers/auth.ts";
import { creatorRoutes } from "./handlers/creator.ts";
import { discoverRoutes } from "./handlers/discover.ts";
import { importRoutes } from "./handlers/imports.ts";
import { messageRoutes } from "./handlers/messages.ts";
import { myPlaceRoutes } from "./handlers/myPlaces.ts";
import { paymentRoutes } from "./handlers/payments.ts";
import { placeSubmissionRoutes } from "./handlers/placeSubmissions.ts";
import { placeRoutes } from "./handlers/places.ts";
import { planRoutes } from "./handlers/plans.ts";
import { postRoutes } from "./handlers/posts.ts";
import { userRoutes } from "./handlers/users.ts";
import { attachStomp } from "./realtime.ts";
import { createRouter } from "./router.ts";

const PORT = Number(process.env.MOCK_PORT ?? 4000);

const handle = createRouter([
  ...authRoutes,
  ...userRoutes,
  ...postRoutes,
  ...placeRoutes,
  ...placeSubmissionRoutes,
  ...importRoutes,
  ...planRoutes,
  ...discoverRoutes,
  ...messageRoutes,
  ...accountRoutes,
  ...myPlaceRoutes,
  ...creatorRoutes,
  ...paymentRoutes,
  ...adminRoutes,
]);

const server = createServer(handle);
// 채팅 STOMP (ws://localhost:4000/api/v1/ws). 프론트는 NEXT_PUBLIC_WS_URL로 연결합니다.
attachStomp(server);
server.listen(PORT, () => {
  console.log(`[mock] http://localhost:${PORT}/api/v1 · ws://localhost:${PORT}/api/v1/ws`);
});
