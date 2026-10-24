# Closie — Your Personal AI Stylist

A multi-brand-ready fashion discovery and styling MVP. React/Vite fronts the existing Java 21 / Spring Boot 4.1 backend and PostgreSQL 17 catalog. Discover → Find → Style uses database records, deterministic hard filters, conversational preferences and budget-checked outfit assembly. Optional LLM interpretation processes user text only.

**Live-data limitation:** no product feed is bundled and no live eBay/OpenAI call was verified. The default catalog view is honestly empty until an authorized source is configured and imported. Test fixtures are isolated in PostgreSQL Testcontainers and never inserted into the development database.

## Local setup

Requirements: Java 21, Docker, Node 22.12+ and pnpm 11. Existing database names and Compose volume are unchanged to preserve data. No deployment is included.

From this checkout:

```sh
docker compose up -d
./mvnw spring-boot:run
```

In a second terminal:

```sh
cd frontend
pnpm install
pnpm dev
```

Open http://127.0.0.1:5173. Vite proxies `/api` to http://127.0.0.1:8080. Set `CLOSIE_API_URL` for a different backend. `pnpm build` creates the frontend production bundle; `pnpm preview` is static preview only and does not proxy APIs. For a same-origin local packaged run, copy `frontend/dist/` into `target/classes/static/` after backend compilation and before packaging, or configure a production reverse proxy in a future deployment. Never place secrets in Vite variables.

Database defaults: `fashion_search`, user/password `fashion` (local development only). Overrides: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Environment placeholders are in `.env.example`; Spring Boot does not automatically load that file. Export values in the terminal or configure the IDE. Do not commit secrets.

On macOS with Colima, tests may need:

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

If another Java is the default, select your installed Java 21 through `JAVA_HOME` for these commands.

## Real catalog import

Review [provider policy](docs/PROVIDER_POLICY.md) and your account's permissions first. The existing import endpoint requires `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, and `IMPORT_TOKEN` on the backend. Restart after configuring them.

```sh
curl -X POST http://127.0.0.1:8080/api/admin/products/import \
  -H "X-Import-Token: $IMPORT_TOKEN"
```

Eight initial keyword groups cover jackets, cardigans, dresses, sneakers, trousers, blouses, necklaces and earrings. Defaults are two pages of 50 per query (up to 800 occurrences, not 800 unique products). Imports upsert by `(source, external_id)` and refresh `last_seen_at` even for unchanged records. Counts report primary-field creates/updates and invalid skipped records; observation-only metadata refreshes need not increment `updated`. Each fetched page commits separately; a later error leaves earlier pages committed. Keep the import endpoint private; concurrency controls/retry jobs are future work.

`EBAY_DISPLAY_ENABLED=true` is an explicit operator opt-in **after** provider review, not a claim of authorization. Only observations from the past six hours with HTTPS images and product links appear. Existing old records require refresh. Displayed availability is unknown unless the provider supplied it. No scheduler or live checkout/stock validation is implemented.

Other providers require an authorized `ProductSource` adapter and an explicit `CLOSIE_PERMITTED_SOURCES` allowlist. No retailer scraping, unauthorized feed, sample inventory, or invented brand metadata is included.

## AI configuration and fallback

Default: functional deterministic interpretation and styling, no external model calls. For optional natural-language preference interpretation, configure backend-only `OPENAI_API_KEY`, `OPENAI_MODEL` (a Responses/structured-output-capable model available to your account), and `CLOSIE_AI_ENABLED=true`. Missing configuration or request failures fall back to rules. The configured model is not assumed to be accessible and live model access was not tested.

Only user-entered text/preferences leave the application. Product existence, hard filters, currencies, price arithmetic, slot coverage, links and expiry remain server-side. Catalog content never goes to the LLM. No vector search or embeddings are enabled.

## API

- `GET /api/discover?category=TOP&maxPrice=150&currency=USD&aesthetic=feminine&page=0`
- `GET /api/search?q=cardigan&maxPrice=100&brand=Example&color=ivory&currency=USD`
- `POST /api/search`: `{ "message": "outfit for college under $150", "context": null, "filters": null, "page": 0 }`
- Follow up with `{ "message": "Something more feminine", "context": <previous response.context> }`.
- `GET /api/products/{id}` returns a display-permitted product or 404.
- `POST /api/style`: `{ "message": "feminine outfit for college", "filters": { "maxPrice": 150, "currency": "USD" }, "anchorId": null }`.
- `POST /api/admin/products/import`: authenticated import, existing contract retained.

Search returns context, interpretation mode and paginated results (24/page). Explicit UI filters take precedence. Brand/color only match provided metadata. Text search is case-insensitive AND-token keyword matching; synonyms/semantic matching are limited. Rules recognize common categories, colors, aesthetics, occasions, maximum/minimum prices and cheaper refinements. Reset clears context; arbitrary per-field conversational negations are not supported. SQL ranking prefers supported aesthetic/occasion terms then recent observations, with stable ID tie-breaking.

Style uses top + bottom + shoes, or one-piece + shoes. Outerwear/accessories can serve as optional anchors, but are not automatically added. All items use one currency. Cheapest compatible slot coverage is established first, then preference-matching upgrades are chosen within budget. The request budget means **item subtotal**, excluding tax/shipping. Aesthetic/occasion preferences are soft; there is no claim of perfect fashion compatibility or size fit. A partial outfit reports missing slots honestly. Selection is bounded to 1,000 lowest-priced eligible candidates and exposes `candidateLimitReached`; this is not exhaustive optimization. Unknown-stock items remain marked for retailer confirmation; unavailable/expired items are excluded.

Saved IDs and optional preferences use browser storage. The Saved toggle shows saved items in the currently loaded view, not a cross-device saved-items service. No accounts, checkout or production authentication were added.

## Tests

```sh
./mvnw test
cd frontend
pnpm build
pnpm exec playwright install chromium
cd ..
CLOSIE_BROWSER_SMOKE=true ./mvnw -Dtest=CatalogJourneyTest test
```

The last command starts isolated PostgreSQL, runs the actual Spring HTTP backend, seeds clearly labeled **test-only** products and launches Vite/Chromium for Discover → Find → refinement → Style. It also checks mobile layout/empty states and an over-budget anchor error. It mocks only test image delivery; no API responses are mocked. Requires Node on PATH. Set `CLOSIE_NODE` to an absolute node executable if needed. Browser smoke is opt-in during ordinary Maven tests. Database containers and Vite are cleaned up after tests. Test product URLs are explicitly `example.test`, not live retailer links.

See [architecture](docs/ARCHITECTURE.md) and [provider restrictions](docs/PROVIDER_POLICY.md). The legacy `PROJECT_SPEC.md` is retained for historical context; the V2 direction and scope in these documents supersede conflicting V1 non-goals.
