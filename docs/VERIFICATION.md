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
