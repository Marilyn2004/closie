> Archived retail-era README. Historical claims and verification dates below are not the current concept-studio status. See the root README and STUDIO_VERIFICATION.md.

# Closie — Your Personal AI Stylist

Closie is a multi-brand-ready fashion discovery and styling application built as a Software Engineering portfolio project. Browse a visual catalog, find a piece in natural language, refine the request, and assemble a coordinated outfit within an item budget.

**Current verification boundary:** the complete application is verified with an explicitly labeled original sample collection. On 2026-10-27, Production eBay OAuth and Browse search returned 200, and real eBay account-deletion notifications passed signature verification on the Railway backend. The live catalog has not been populated; the conservative notification handler has triggered its import-pause protection. Live OpenAI access remains unverified. Sample designs, illustrations, studio names and prices are fictional and are never presented as retailer inventory or items for sale. Real catalog mode remains separate.

## eBay production key activation

The optional signed account-deletion callback and activation instructions are in [docs/EBAY_SETUP.md](docs/EBAY_SETUP.md). It is disabled by default. A stable public HTTPS endpoint and account-side verification remain outstanding. Review its conservative cache-purge/import-pause policy before enabling.

## Railway deployment

See [docs/RAILWAY_DEPLOYMENT.md](docs/RAILWAY_DEPLOYMENT.md) for the dedicated trial deployment, runtime configuration and verification boundaries. Cloud mode starts with an empty real catalog; local sample mode remains separate.

## Start locally

Requirements: Java 21, Node 22.12+ (Node 24 recommended for the tested tooling), pnpm 11, and a running Docker engine/Colima. Run from this checkout:

```sh
./dev demo
```

Open **http://127.0.0.1:5173**. The command creates the isolated `closie_demo` database if needed, installs missing frontend dependencies, compiles the backend, starts both services, checks API readiness, and stays running. Keep that terminal open. In another terminal, stop only this checkout's application processes with:

```sh
./dev stop
```

Database containers and data are preserved. The app also stops when the launcher exits. Logs are in ignored `.local/backend.log` and `.local/frontend.log`. The launcher refuses occupied app ports or unexpected database mappings rather than connecting to unrelated services. Startup requires network access on a fresh machine to obtain dependencies/images.

### Database isolation

The launcher uses the dedicated `closie-v2-local-db` container on **127.0.0.1:55432**, preserving the existing `closie_v2_local_pgdata` volume if present. It never uses the unrelated PostgreSQL instance on 5432.

- `demo` mode: `closie_demo`, original sample records only, seeding idempotent.
- `live` mode: `fashion_search`, existing real-catalog records preserved, no sample seeding.

Demo mode rejects a nonlocal/non-demo datasource before Flyway initializes; the seeder also checks the actual database name. Do not enable demo mode against another database. Tests use their own disposable Testcontainers databases and never these development databases.

To run live-catalog mode:

```sh
./dev live
```

It is honestly empty until a permitted provider has been imported. The separate `docker-compose.yml` is an alternative for a **new** database setup on port 55432; do not run it alongside the launcher's existing database container. The launcher is the supported path for this local environment.

### Manual startup

With the correct database already running:

```sh
# Backend, sample database
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
# Or backend, existing real catalog
DB_URL=jdbc:postgresql://127.0.0.1:55432/fashion_search ./mvnw spring-boot:run
```

In another terminal:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Vite proxies `/api` and `/demo` to the backend on 8080. `CLOSIE_API_URL` overrides that backend target. `pnpm preview` previews static assets only and is not a complete backend-connected deployment. No production deployment has been performed.

## Features

- **Discover:** editorial, image-led feed; categories including bags and jewelry; aesthetic/budget preferences; preference, price and recency sorting; pagination; sample/live mode labeling.
- **Find:** keyword retrieval with hard price/category/brand/color/currency filters, structured query understanding, and conversational refinement retaining previous constraints. Explicit UI fields override natural-language fields. Common resets such as “any color” and “any brand” are supported.
- **Style:** natural-language occasion, aesthetic, mood, brand-inspired vibe, budget and optional anchor. “Aritzia vibe” is a soft style preference, not an invented merchant integration or hard brand requirement. The browser retains a styling session for follow-ups. Backend selection validates identity, eligibility, currency, slot composition, uniqueness and item-subtotal budget. Optional bags/accessories fit inside the remaining budget; requested outerwear is supported.
- **Saved:** browser-local IDs resolved against current catalog eligibility, across pages and reloads. Unavailable saves are reported without showing stale cached prices. Demo and live saves have separate namespaces.
- **My Style:** aesthetic, mood and typical budget stored only in the current browser.
- Loading, empty, unavailable, error and honest partial-outfit states; responsive desktop/mobile layouts and native accessible product-detail dialogs for sample designs.

Sample catalog contains 29 records across tops, bottoms, dresses, outerwear, shoes, bags and accessories: 26 USD and 3 EUR. GBP requests are supported but the sample collection has no GBP products, so the app returns honest empty/partial results. Sample records have no purchase URL. Real cards use the provider's actual outbound URL and display availability uncertainty.

## Tech stack and architecture

Java 21, Spring Boot 4.1.1, Spring MVC/RestClient, Spring Data JPA, Flyway, PostgreSQL 17/pgvector, Maven; React 19, Vite 7, browser storage; JUnit, Mockito, PostgreSQL Testcontainers and Playwright/Chromium.

```text
React: Discover / Find / Style / Saved
         ↓ REST / validated DTOs
QueryInterpreter → CatalogService → OutfitService
       ↓ optional AI           ↓ authoritative checks
OpenAI Responses          PostgreSQL catalog
                              ↑
Authenticated / scheduled import → ProductSource → provider API
```

`ProductSource` and `SourceProduct` are provider-neutral ingestion boundaries. eBay-specific DTOs remain inside its client/adapter. UI and styling consume `ProductView`, not provider responses. Sample illustrations are original SVG assets served by Spring and proxied during frontend development. No image scraping or retailer branding is used.

See [architecture and decisions](docs/ARCHITECTURE.md), [requirements scope](docs/REQUIREMENTS.md) and [provider policy](docs/PROVIDER_POLICY.md).

## Database

- V1: products and `(source, external_id)` uniqueness; installs the pgvector extension.
- V2: additive brand/retailer/slot/color/size/availability/last-seen/style/occasion metadata and filtering indexes.
- V3: durable catalog import runs with start/end time, status, counts and sanitized error type.

Old rows are preserved. No embeddings or vector index are implemented. Unknown provider facts remain null/UNKNOWN. Outfit-slot classification from title/category is a documented heuristic.

## Environment and secrets

The launcher loads optional **ignored `.env.local`** from this checkout. Spring Boot alone does not automatically load that file. Configure credentials through your environment/IDE; never put them in Vite variables or version control. `.env.example` lists configuration without secrets.

- `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `IMPORT_TOKEN`: authenticated eBay import.
- `EBAY_DISPLAY_ENABLED=false`: explicit operator opt-in after reviewing source authorization/display terms. Defaults safe.
- `EBAY_ENRICH_DETAILS=false`, `EBAY_DETAILS_PER_PAGE=5`: optional bounded item-detail requests for provider-supplied brand, color and size. Missing facts are not guessed.
- `EBAY_PAGE_SIZE=50`, `EBAY_MAX_PAGES=2`, `EBAY_REQUEST_INTERVAL_MS=200`: bounded ingestion/request pacing.
- `CLOSIE_REFRESH_ENABLED=false`, `CLOSIE_REFRESH_INTERVAL_MS=14400000`: optional refresh, normally every four hours, minimum one hour. Requires credentials; demo mode disables it.
- `CLOSIE_PERMITTED_SOURCES`: explicit display allowlist for future reviewed non-eBay adapters; the sample source cannot be enabled through this allowlist.
- `CLOSIE_AI_ENABLED=false`, `OPENAI_API_KEY`, `OPENAI_MODEL`: optional model access. Select a Responses/structured-output-capable model available to your account; no particular model access is assumed.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: manual real-catalog configuration. Launcher fixes the target to the dedicated local container; demo profile is guarded.

## eBay integration and freshness

Live verification is blocked by missing credentials/account authorization. The HTTP implementation is tested with actual localhost transport and mocked provider responses; that does not prove production API access.

Import in real-catalog mode, after configuring backend credentials and reviewing permissions:

```sh
curl -X POST http://127.0.0.1:8080/api/admin/products/import \
  -H "X-Import-Token: $IMPORT_TOKEN"
```

Ten search queries cover all primary outfit slots. Import supports OAuth caching/one-time renewal after revoked tokens, bounded transient-error retries, request pacing, Retry-After cooldown, single-process overlap rejection, normalization, deduplication, and per-page persistence. A later failed page leaves earlier committed pages intact; import history reports the run as failed. Process crashes can leave RUNNING history rows; there is no distributed job system.

Optional detail enrichment extracts only provider-supplied aspects. Non-enriched listings can lack brand/color/size and therefore cannot satisfy those exact filters. The fixed-price request is retained, but marketplace leaf-category behavior still requires real-provider validation. No live stock or size guarantee is made.

Displayed eBay observations must be within the configured six-hour policy. Scheduled refresh re-observes bounded search pages; it is not exhaustive reconciliation of every historical listing. Stale records are hidden without deletion. Third-party retention/deletion obligations remain operator responsibilities under applicable agreements. Configure API limits and authorized use before enabling automatic calls.

## Actual AI behavior

AI is optional and off by default. The UI distinguishes successful AI-assisted interpretation/curation from the working rules-based stylist.

1. The query model sees only user requests and previous preferences, using a strict JSON schema.
2. The outfit model can evaluate a bounded shortlist and explain its selection **only for original Closie demo content in this release**. No eBay or other third-party catalog content is sent to it. Additional providers need a separate documented processing-permission review and code change.
3. The backend rejects invented/duplicate IDs, invalid compositions, missing anchors, mixed currencies and over-budget proposals. It re-reads current catalog facts before returning the result. Model output never supplies authoritative prices or links.
4. Missing configuration, HTTP errors, incomplete/malformed responses, invalid proposals and timeouts fall back honestly to rules.

Requests use `store:false`, a 12-second deadline, bounded tokens/input/response sizes and at most two concurrent model calls. A styling request uses at most one interpretation call and one outfit call; no recursive agent loop or training/embedding pipeline is included. Third-party content is treated as untrusted data, never instructions. Live model access and quality have not been verified without credentials.

Fallback selection scores aesthetic/occasion/mood/vibe terms and palette compatibility across bounded template combinations, then adds optional finishing pieces. This is explainable fashion guidance, not a guarantee of fit or universally correct styling. Budget is item subtotal in one currency, excluding tax/shipping. Clothing templates are top+bottom+shoes or one-piece+shoes. Candidate retrieval caps at 1,000 low-priced eligible records, and each core slot considers its top six matches plus two cheap options. Partial results report missing slots rather than inventing items.

## API overview

- `GET /api/status`: sample/live mode and nonsecret configuration/readiness indicators.
- `GET /api/discover?category=TOP&maxPrice=150&currency=USD&aesthetic=feminine&sort=price_asc&page=0`
- `GET /api/search?q=black+leather+jacket&maxPrice=100&currency=USD`
- `POST /api/search`: `{ "message": "outfit for college under $150", "context": null, "filters": null, "page": 0, "sort": "relevance" }`
- Follow up by passing the previous response's `context` with a new `message`.
- `GET /api/products/{id}`: eligible product details or 404.
- `POST /api/products/saved`: `{ "ids": [1, 2] }`, max 200; returns current products and unavailable IDs.
- `POST /api/style`: `{ "message": "effortless feminine coffee date with an Aritzia vibe, budget is $150", "context": null, "filters": null, "anchorId": null, "includeExtras": true }`
- `POST /api/admin/products/import`: requires `X-Import-Token`; never expose as an unprotected public operation.

## Testing

```sh
./mvnw test
cd frontend
pnpm build
pnpm exec playwright install chromium
cd ..
CLOSIE_BROWSER_SMOKE=true ./mvnw test
```

On macOS/Colima, set these for test execution if needed (no global configuration changes required):

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Browser smoke is opt-in for ordinary Maven tests. It starts the actual backend on a random port, Vite on 5174, and disposable PostgreSQL, then runs separate original-catalog and demo-collection journeys. Positive API responses are not mocked. Test-only images are mocked in the original fixture journey; demo images come from the backend. No live third-party API or model is contacted. `CLOSIE_NODE` can supply an absolute Node executable.

The CI workflow runs backend/database/browser tests and frontend build on GitHub Actions, but has not been pushed or remotely executed. See [verification](docs/VERIFICATION.md) for the actual local results and explicit verification limits. Format frontend with `pnpm format`.

## Screenshots

Sample collection only — original illustrations and fictional prices, not live merchandise.

![Sample Discover](docs/screenshots/discover.png)
![Sample Style](docs/screenshots/style.png)

## Remaining limitations and expansion

No authorized live multi-retailer feed, actual stock/fit validation, live OpenAI evaluation, public deployment, full-text/semantic retrieval, complex accounts or checkout. Rules support common phrases rather than unrestricted natural-language negation/synonyms. Saved preferences stay on one browser; no cross-device sync. Search is AND-token SQL matching and may miss synonyms. The catalog model is multi-provider-ready; real source-specific licensing, freshness, attribution and retention rules must be implemented/reviewed before adding each retailer.

Do not present the sample demo as verified live commerce or claim merchant partnerships, real traffic, or measured performance improvements. The next external step is authorized eBay/account access and controlled real-product/model smoke tests, not more speculative infrastructure.
