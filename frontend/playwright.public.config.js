import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  testMatch: "public-demo.spec.js",
  timeout: 30000,
  workers: 1,
  use: { baseURL: "http://127.0.0.1:5177", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5177",
    url: "http://127.0.0.1:5177",
    reuseExistingServer: false,
    timeout: 20000,
  },
});
