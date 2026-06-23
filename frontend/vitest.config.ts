import { defineConfig, type ViteUserConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Vitest configuration. Included in tsconfig.node.json so the IDE and `tsc -b`
// both resolve its imports (bundler resolution).
//
// react() is typed against this project's Vite 8 install, while Vitest 2.1.x
// bundles its own older Vite (under node_modules/vitest/node_modules/vite) — the
// two Vite majors are an unsupported combo, so their Plugin types are structurally
// incompatible at compile time. We bridge react()'s plugin to the type Vitest's
// config expects. Runtime is unaffected: Vitest runs the plugin with its own Vite.
export default defineConfig({
  plugins: [react()] as unknown as ViteUserConfig['plugins'],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    css: false,
    // The whole SPA is one large component tree that re-renders on every
    // interaction; under jsdom a single flow test (render + several awaited
    // state transitions) can take a few seconds, so the 5s default is too tight.
    testTimeout: 20000,
    coverage: {
      provider: 'v8',
      // text -> build log; lcov -> SonarQube; cobertura -> CodeBuild Reports tab.
      reporter: ['text', 'lcov', 'cobertura'],
      include: ['src/**/*.{ts,tsx}'],
      // types.ts is pure type declarations (no runtime code), like vite-env.d.ts.
      exclude: ['src/**/*.test.{ts,tsx}', 'src/main.tsx', 'src/vite-env.d.ts', 'src/types.ts'],
      // ── 80% coverage gate ──────────────────────────────────────────────
      // Mirrors the backend JaCoCo gate (80% lines). `npm run coverage` exits
      // non-zero when any metric drops below the bar, which fails the Amplify
      // build (amplify.yml runs `npm run coverage`) and the S3 frontend
      // pipeline (buildspec-frontend.yml). thresholds use total project counts.
      thresholds: {
        lines: 80,
        statements: 80,
        functions: 80,
        branches: 80,
      },
    },
  },
})