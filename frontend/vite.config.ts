import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// NOTE: the Vitest configuration lives in vitest.config.ts (kept separate so this
// file — which the production build type-checks — stays on pure Vite types).
export default defineConfig({
  plugins: [react()],
})