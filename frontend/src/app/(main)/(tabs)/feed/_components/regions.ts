/** 지역 트리 노드. children이 있으면 하위 선택 가능, 없으면 최하위(동 레벨) */
export interface RegionNode {
  name: string;
  children?: RegionNode[];
}

/**
 * 지역 트리.
 * - 현재는 서울특별시 > 강남구 > 동까지만 목 데이터 구성
 * - 나머지 시도는 동 없이 시도 레벨만 선택 가능
 * - 추후 다른 구/시 추가 시 children 배열에 추가
 */
export const REGION_TREE: RegionNode[] = [
  {
    name: "서울특별시",
    children: [
      {
        name: "강남구",
        children: [
          { name: "개포1동" },
          { name: "개포2동" },
          { name: "개포4동" },
          { name: "논현1동" },
          { name: "논현2동" },
          { name: "대치1동" },
          { name: "대치2동" },
          { name: "대치4동" },
          { name: "도곡1동" },
          { name: "도곡2동" },
          { name: "삼성1동" },
          { name: "삼성2동" },
          { name: "세곡동" },
          { name: "수서동" },
          { name: "압구정동" },
          { name: "역삼1동" },
          { name: "역삼2동" },
          { name: "율현동" },
          { name: "일원1동" },
          { name: "일원2동" },
          { name: "일원본동" },
          { name: "자곡동" },
          { name: "청담동" },
        ],
      },
    ],
  },
  { name: "부산광역시" },
  { name: "대구광역시" },
  { name: "인천광역시" },
  { name: "광주광역시" },
  { name: "대전광역시" },
  { name: "울산광역시" },
  { name: "세종특별자치시" },
  { name: "경기도" },
  { name: "강원특별자치도" },
  { name: "충청북도" },
  { name: "충청남도" },
  { name: "전북특별자치도" },
  { name: "전라남도" },
  { name: "경상북도" },
  { name: "경상남도" },
  { name: "제주특별자치도" },
];

/**
 * fullPath("서울특별시 강남구 역삼1동") 형태의 문자열에서 표시용 짧은 레이블 생성.
 * 예) "서울특별시 강남구 역삼1동" → "역삼1동"
 *     "서울특별시 강남구"         → "강남구"
 *     "서울특별시"                → "서울"  (접미사 제거)
 */
export function regionLabel(fullPath: string): string {
  const parts = fullPath.trim().split(" ");
  const last = parts[parts.length - 1];
  // 최상위(시도)만 선택됐을 때 접미사 제거해서 짧게 표시
  if (parts.length === 1) {
    return last
      .replace("특별자치시", "")
      .replace("특별자치도", "")
      .replace("특별시", "")
      .replace("광역시", "")
      .replace("도", "");
  }
  return last;
}

/** path 배열(예: ["서울특별시","강남구"])에서 해당 노드 찾기 */
export function findNode(path: string[]): RegionNode | null {
  let nodes: RegionNode[] = REGION_TREE;
  let node: RegionNode | null = null;
  for (const segment of path) {
    node = nodes.find((n) => n.name === segment) ?? null;
    if (!node) return null;
    nodes = node.children ?? [];
  }
  return node;
}
