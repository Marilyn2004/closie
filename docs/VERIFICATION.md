> Historical retail verification (2026-10-26/27). For current concept-demo evidence use [PUBLIC_DEMO_RELEASE.md](PUBLIC_DEMO_RELEASE.md) and [PR_PREPARATION.md](PR_PREPARATION.md). Old blanket-purge and deployment statements describe their dated implementation; current seller-aware behavior is in [EBAY_SETUP.md](EBAY_SETUP.md). No current cloud state is asserted.

# Verification — 2026-10-26

Latest implementation was verified locally with Java 21, PostgreSQL 17 Testcontainers, Node 24 and Chromium. These are measured local results, not claims about deployed traffic or external accounts.

## Automated results

- Java/Maven: **73 tests, 0 failures, 0 errors, 0 skips**, with `CLOSIE_BROWSER_SMOKE=true`.
- Chromium: **5 passing scenarios** launched by the Java integration tests against actual Spring HTTP backends and isolated PostgreSQL.
- Frontend production build: passed.
- Java source compilation, Git whitespace checks and launcher shell syntax: passed.

The non-database suite covers rules/refinements, exact constraints and descriptive keywords, model request schema/response handling with a localhost stub, invalid AI preferences, no third-party-to-model calls, eBay token renewal/retry/cooldown/provider aspects, normalization, import overlap rejection and wrong-demo-database protection. The database suite exercises migrations, persistence/uniqueness, SQL search/filter/sort, stale/provider exclusion, Saved lookup, import failure history with earlier pages preserved, sample seeding idempotence, candidate grounding, exact subtotals, single currency, partial outfits and rejected model proposals.

Browser scenarios:
1. Original controlled catalog: Discover → Find → refine → anchor → Style, outbound URL propagation, save, budget error.
2. Original catalog: mobile navigation, empty results and overflow check.
3. Sample collection: populated image-led Discover, detail dialog, natural-language Find, refinement, conversational Style, exact sum, no-sale labeling, Saved across navigation/reload.
4. Sample collection: mobile complete outfit and honest impossible-budget partial result.
5. Sample collection: natural-language EUR budget produces a single-currency €95 outfit.

Positive API responses are not mocked. Original fixture images are mocked for deterministic testing; sample illustrations are served from the backend. The sample/test products and model test responses are not live merchant inventory or live model output.

## Actual local launch

`./dev demo` was run against the dedicated local container. It created only `closie_demo`, seeded 29 application-authored sample records, started the backend and Vite, and passed `/api/status` readiness checks through the frontend proxy. The runtime reported demo mode, AI not configured, and 26 eligible USD samples (three further EUR samples).

The mission's coffee-date/Aritzia-vibe/$150 prompt produced a grounded, rules-based five-piece sample outfit totaling **$149**. This is a functional sample demonstration, not a validated real-product recommendation or model response. Desktop and mobile screenshots were visually inspected; committed images in docs/screenshots are explicitly sample-mode output.

The launcher keeps a foreground session alive, and `./dev stop` stops only its tracked application processes. Databases and data are preserved. App ports and database mapping are checked before launch. The dedicated database uses port 55432, not the unrelated service on 5432.

## External verification limits

EBAY_CLIENT_ID, EBAY_CLIENT_SECRET, IMPORT_TOKEN, OPENAI_API_KEY and OPENAI_MODEL were not configured in the execution session. No live provider import, real stock/size, merchant authorization, third-party image rights, production API filtering, live model access or model styling quality was verified. No scraping, remote account changes, push, merge or production deployment occurred. CI configuration is committed but has not been remotely executed.

## Remaining engineering limits

Keyword/rule interpretation is bounded, not unrestricted natural-language understanding. Style uses explainable heuristics and bounded candidate combinations. No embeddings, full authentication, checkout, cross-device storage, exhaustive inventory reconciliation or distributed scheduling. Other provider display/processing/retention policies need separate authorization review. Historical live records are preserved; no sample rows are inserted into the live database.

Reproduce using README.md. Browser logs: target/browser-smoke.log and target/demo-browser-smoke.log. Java results: target/surefire-reports. Ordinary Maven tests skip the two opt-in browser launcher methods unless CLOSIE_BROWSER_SMOKE=true.

## eBay notification callback update — 2026-10-27

Final regression: **88 Java tests, zero failures/errors/skips**, including the opt-in launchers for **five passing Playwright browser cases**. Fifteen additional Java cases cover notification configuration, real generated ECC signatures, tampering, key caching/outage pacing, the authenticated fixed-host public-key request and one-time OAuth renewal, exact challenge output, signed HTTP-to-PostgreSQL deletion, idempotent retries, in-flight import suppression, transaction rollback and invalid/oversized/duplicate-key JSON. All database mutation tests use disposable Testcontainers databases.

The token preparation helper was also checked in an isolated temporary directory for owner-only permissions, no token disclosure, stable repeated generation, preservation of existing settings and rejection of invalid callback URLs. The private local verification token has been generated; notification handling remains disabled. No development catalog data was deleted, no live database migration was run, and the existing application processes were left running.

Full test log: `/tmp/closie-notification-final-tests.log`; Java reports: `target/surefire-reports`. A first attempt inside the restricted execution environment could not access Docker/Mockito attachment; the complete suite was rerun successfully with the required local process permissions.

The Caddy configuration is an undeployed template, checked against its official request-body directive documentation but not executed by Caddy. No real eBay notification or activation call was made. A stable public HTTPS host, Production credentials, account-side challenge/test delivery and the documented seller-aware deletion/backup policy remain external/live-commerce blockers. See `docs/EBAY_SETUP.md` before activation.

## Authorized Railway deployment — 2026-10-27

Following explicit deployment authorization, a dedicated Closie app and private PostgreSQL service were created using the existing Railway trial. Both report SUCCESS. Public frontend, empty live catalog, exact callback challenge, unsigned-notification rejection, import-token enforcement, saved resolution, browser search and empty-catalog styling were verified. No eBay account-side activation, production credentials, live product ingestion or real model access was verified. No local data was copied or deleted. See `RAILWAY_DEPLOYMENT.md` for a sanitized historical summary; trial figures and resource IDs are not current deployment instructions.

## Real eBay connection and notification verification — 2026-10-27

Production OAuth: HTTP 200. Production Browse: HTTP 200, five real listings with required display facts and seller identity present. The then-current cloud deployment after the user's runtime credential changes was reported successful. This refers to Railway configuration, not a Git credential commit. User-reported eBay test notification success is corroborated by backend processing logs after actual signature verification, with zero product rows purged and import-pause protection triggered. Direct database receipt querying was unavailable without a registered SSH key; none was added. No live catalog import or model request occurred. Seller-aware deletion/reimport suppression was a blocker at that time; V5 and the current policy now implement it, subject to the operational limits in `EBAY_SETUP.md`. Documentation-only updates followed these checks; no new application code or tests were changed.
