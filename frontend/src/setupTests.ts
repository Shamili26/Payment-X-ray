// Vitest global setup: adds jest-dom matchers (toBeInTheDocument, etc.) and
// resets mocks/global stubs between tests so each test starts from a clean slate.
// The "/vitest" entry both registers the matchers AND augments Vitest's
// `expect` types, so `toBeInTheDocument()` type-checks in the test files.
import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});