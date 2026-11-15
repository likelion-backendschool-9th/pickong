import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

/**
 * 계층 import 규칙 (BLUEPRINT.md 5장).
 * eslint-plugin-boundaries 도입 전까지 no-restricted-imports로 핵심 규칙만 강제합니다.
 */
const sliceInternals = {
  group: ["@/entities/*/*", "@/features/*/*", "@/widgets/*/*"],
  message: "슬라이스 내부 경로 대신 공개 API(index.ts)로 import하세요. 예: @/entities/place",
};
const sharedUiBarrel = {
  name: "@/shared/ui",
  message: "shared/ui는 파일 경로로 import하세요. 예: @/shared/ui/Button",
};
const layer = (patterns, message) => ({ group: patterns, message });

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", { paths: [sharedUiBarrel], patterns: [sliceInternals] }],
    },
  },
  {
    files: ["src/shared/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        { patterns: [layer(["@/entities*", "@/features*", "@/widgets*", "@/app*"], "shared는 상위 계층을 import할 수 없어요.")] },
      ],
    },
  },
  {
    files: ["src/entities/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [sharedUiBarrel],
          patterns: [sliceInternals, layer(["@/features*", "@/widgets*", "@/app*"], "entities는 features·widgets·app을 import할 수 없어요.")],
        },
      ],
    },
  },
  {
    files: ["src/features/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [sharedUiBarrel],
          patterns: [sliceInternals, layer(["@/features*", "@/widgets*", "@/app*"], "feature끼리 import하지 말고 위 계층(widgets·app)에서 조합하세요.")],
        },
      ],
    },
  },
  {
    files: ["src/widgets/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        { paths: [sharedUiBarrel], patterns: [sliceInternals, layer(["@/widgets*", "@/app*"], "widgets는 다른 widget·app을 import할 수 없어요.")] },
      ],
    },
  },
  {
    // mock 서버는 앱 번들 밖의 개발 도구라 요청 본문 타입을 느슨하게 둡니다.
    files: ["mocks/**/*.ts"],
    rules: { "@typescript-eslint/no-explicit-any": "off" },
  },
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
]);

export default eslintConfig;
