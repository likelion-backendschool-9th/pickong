/**
 * 개발용 mock API 서버 (localhost:4000).
 * 앱 번들에 포함되지 않으며, next.config.ts의 rewrites가 /api/*를 이 서버로 전달합니다.
 * 실제 백엔드로 바꿀 때는 API_BASE_URL만 변경합니다.
 *
 * 실행: npm run mock (npm run dev에 포함)
 */
import { createServer } from "node:http";
import { accountRoutes } from "./handlers/account.ts";
import { adminRoutes } from "./handlers/admin.ts";
import { authRoutes } from "./handlers/auth.ts";
import { creatorRoutes } from "./handlers/creator.ts";
import { discoverRoutes } from "./handlers/discover.ts";
import { groupChatRoutes } from "./handlers/groupChats.ts";
import { importRoutes } from "./handlers/imports.ts";
import { messageRoutes } from "./handlers/messages.ts";
import { myPlaceRoutes } from "./handlers/myPlaces.ts";
import { paymentRoutes } from "./handlers/payments.ts";
import { placeRoutes } from "./handlers/places.ts";
import { planRoutes } from "./handlers/plans.ts";
import { postRoutes } from "./handlers/posts.ts";
import { userRoutes } from "./handlers/users.ts";
import { createRouter } from "./router.ts";

const PORT = Number(process.env.MOCK_PORT ?? 4000);

const handle = createRouter([
  ...authRoutes,
  ...userRoutes,
  ...postRoutes,
  ...placeRoutes,
  ...importRoutes,
  ...planRoutes,
  ...discoverRoutes,
  ...messageRoutes,
  ...accountRoutes,
  ...myPlaceRoutes,
  ...creatorRoutes,
  ...paymentRoutes,
  ...groupChatRoutes,
  ...adminRoutes,
]);

createServer(handle).listen(PORT, () => {
  console.log(`[mock] http://localhost:${PORT}/api`);
});
