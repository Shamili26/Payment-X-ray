import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Vitest configuration. Kept separate from vite.config.ts because the production
// build (tsc -b) type-checks vite.config.ts, and mixing Vitest's bundled Vite
// types with the project's Vite 8 types trips the type-checker. This file is not
// part of any tsconfig include, so it is only ever loaded by Vitest at runtime.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/main.tsx', 'src/vite-env.d.ts'],
    },
  },
})