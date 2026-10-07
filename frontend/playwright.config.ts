import { defineConfig, devices } from '@playwright/test'

// End-to-end tests (e2e/*.e2e.ts) against a running deployment of the whole shop:
// in CI, the staging environment the pipeline starts from the new images
// (deploy/docker-compose.staging.yml); by hand, any deployment you can sign in to
// as an admin:
//
//   E2E_BASE_URL=https://localhost E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... npm run e2e
//
// The files are named *.e2e.ts so Vitest, which picks up *.test.* and *.spec.*,
// leaves them alone.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // One at a time: the tests share one database and one sign-in rate limit.
  workers: 1,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://localhost',
    // Staging serves localhost with a certificate from Caddy's own authority,
    // which no browser trusts.
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
