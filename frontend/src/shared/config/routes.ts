/** 라우트 경로 상수. 링크 문자열을 직접 쓰지 않고 이 함수를 사용합니다. */
export const routes = {
  feed: () => "/feed",
  search: () => "/search",
  notifications: () => "/notifications",
  post: (id: string) => `/posts/${id}`,
  newPost: () => "/posts/new",
  map: (view?: "list") => (view ? `/map?view=${view}` : "/map"),
  place: (id: string) => `/places/${id}`,
  newPlace: () => "/places/new",
  collections: () => "/collections",
  newCollection: () => "/collections/new",
  collection: (id: string) => `/collections/${id}`,
  editCollection: (id: string) => `/collections/${id}/edit`,
  importLink: () => "/import",
  /** 일정 목록은 내 프로필의 일정 탭에 있습니다. username을 모르면 /plans가 내 프로필로 보냅니다. */
  plans: () => "/plans",
  myPlans: (username: string) => `/users/${username}?tab=plans`,
  newPlan: (placeIds?: string[]) =>
    placeIds?.length ? `/plans/new?places=${placeIds.join(",")}` : "/plans/new",
  plan: (id: string) => `/plans/${id}`,
  planVisits: (id: string) => `/plans/${id}/visits`,
  messages: () => "/messages",
  conversation: (id: string) => `/messages/${id}`,
  user: (username: string) => `/users/${username}`,
  follows: (username: string, tab: "followers" | "following") => `/users/${username}/follows?tab=${tab}`,
  settings: () => "/settings",
  editProfile: () => "/settings/profile",
} as const;
