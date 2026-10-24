# Implementation verification

Verified locally on 2026-10-24 with Java 21, Colima/PostgreSQL Testcontainers and Chromium.

- Full Maven run: 49 tests, 0 failures, 0 errors, 0 skips (browser smoke explicitly enabled).
- Chromium: 2 passing scenarios against the actual Spring HTTP server and isolated PostgreSQL fixtures; API responses are not mocked.
- React/Vite production build: passed.
- Git whitespace checks: passed.
- Original Desktop checkout: remains clean on main; implementation lives on closie-v2 in a separate worktree.

Coverage includes existing persistence/import tests, a real localhost eBay HTTP transport test, provider metadata normalization, exact brand/color/currency/price filters, wildcard escaping, freshness and provider exclusion, conversational price preservation, invalid AI fallback, complete and incomplete outfits, accurate subtotal, duplicate avoidance, dress slot coverage, non-USD details, mobile layout, saved items, outbound URL propagation and over-budget anchor errors.

Browser test catalog is explicitly labeled Fixture/Test, stored only in disposable Testcontainers PostgreSQL. Test images are served by the browser test route. Those are not actual retailer products or a live-inventory demonstration. Screenshots in ignored frontend/test-results show layout verification only.

No eBay credentials, OpenAI API key or model were configured in the execution session. No live eBay import, model inference, account permission, authorized retailer image display, production availability or remote deployment was verified. No existing catalog data or production resources were changed. To finish a live-data demonstration, confirm provider permissions, configure credentials and refresh real products; then rerun the journey with real product links and prices.

Reproduce using the commands in README.md. Browser subprocess output is captured in target/browser-smoke.log. Maven reports are in target/surefire-reports. Tests and Vite clean up their owned resources after execution.
