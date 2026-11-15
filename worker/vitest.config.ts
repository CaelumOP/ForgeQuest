import { defineConfig } from "vitest/config";

// The suite runs twice: against MemStore and against the real D1Store SQL on node:sqlite.
export default defineConfig({
  test: {
    projects: [
      { test: { name: "mem", include: ["test/**/*.test.ts"] } },
      { test: { name: "sqlite", include: ["test/**/*.test.ts"], env: { FQ_STORE: "sqlite" } } },
    ],
  },
});
