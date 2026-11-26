/**
 * 상가(상권)정보 CSV → mocks/fixtures/storePlaces.json 변환.
 * mock 서버가 매번 큰 CSV를 읽지 않도록, 필요한 업종과 열만 골라 미리 만들어 둡니다.
 *
 * 실행 (frontend 폴더에서): npm run mock:stores -- [CSV 경로]
 * 경로를 생략하면 저장소 루트의 서울 CSV를 읽습니다.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseCsvLine, toStorePlace, type StoreCsvRow, type StorePlaceTuple } from "../lib/storePlace.ts";

const DEFAULT_CSV = new URL("../../../Hongdae_Myeongdong_Jamsil_Gangnam_Seongsu-dong_Seoul.csv", import.meta.url);
const OUTPUT = new URL("../fixtures/storePlaces.json", import.meta.url);

const csvPath = process.argv[2] ?? fileURLToPath(DEFAULT_CSV);
const text = readFileSync(csvPath, "utf8").replace(/^﻿/, "");
const [headerLine, ...lines] = text.split(/\r?\n/).filter((line) => line.length > 0);
const header = parseCsvLine(headerLine);

const required: (keyof StoreCsvRow)[] = [
  "상가업소번호", "상호명", "지점명", "상권업종소분류코드", "상권업종소분류명",
  "행정동명", "지번주소", "도로명주소", "경도", "위도",
];
const missing = required.filter((name) => !header.includes(name));
if (missing.length > 0) throw new Error(`CSV에 필요한 열이 없습니다: ${missing.join(", ")}`);

const places: StorePlaceTuple[] = [];
let skippedLines = 0;
for (const line of lines) {
  const values = parseCsvLine(line);
  if (values.length !== header.length) {
    skippedLines++;
    continue;
  }
  const row = Object.fromEntries(header.map((name, i) => [name, values[i]])) as unknown as StoreCsvRow;
  const place = toStorePlace(row);
  if (place) places.push(place);
}

// 실행할 때마다 같은 파일이 나오도록 id 순서로 저장합니다.
places.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));

const output = {
  source: "소상공인시장진흥공단 상가(상권)정보 (공공데이터포털)",
  columns: ["id", "name", "address", "region", "lat", "lng", "category", "description"],
  rows: places,
};
// 한 줄에 장소 하나씩 써서 diff를 읽을 수 있게 합니다.
const body = output.rows.map((row) => JSON.stringify(row)).join(",\n");
writeFileSync(
  OUTPUT,
  `{"source":${JSON.stringify(output.source)},"columns":${JSON.stringify(output.columns)},"rows":[\n${body}\n]}\n`,
);

const byCategory = Object.groupBy(places, (p) => p[6]);
console.log(
  `[mock:stores] ${lines.length}행 중 ${places.length}곳 저장 (${Object.entries(byCategory)
    .map(([c, list]) => `${c} ${list?.length ?? 0}`)
    .join(", ")})${skippedLines ? `, 열 수가 맞지 않아 건너뛴 행 ${skippedLines}` : ""}`,
);
