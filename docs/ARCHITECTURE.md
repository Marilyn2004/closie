# Architecture and interview notes

React/Vite → thin MVC controllers → validated user preferences / catalog specifications / outfit service → PostgreSQL.

ProductSource → SourceProduct → normalization/upsert → products. EbayProductSource owns eBay DTO adaptation; other authorized sources can implement the same contract. DemoCatalog is an explicit application-owned seed path, not a pretend retailer integration.

## Key decisions

- Keep the existing Java/Spring/JPA backend and additive Flyway migrations.
- Use SQL hard constraints and transparent ranking for a small catalog rather than a second search service or embeddings.
- Separate live vs demo at both database and display-policy boundaries. The pre-datasource demo guard rejects the wrong URL; the seeder checks the actual DB name. Samples are never enabled via the live source allowlist.
- Saved IDs are resolved against the backend each time. Browser storage never becomes authoritative for stale product prices.
- Parse natural-language requests into structured context. Full context accompanies refinements; explicit controls override interpreted values.
- Search category/keywords identify the desired piece. Styling maps these to a template while complementary slots remain independently retrieved. Brand/color constraints apply to eligible candidates; aesthetic/mood/vibe/occasion are soft relevance signals.
- Enumerate bounded slot combinations, choose a budget-safe coherent base, then optionally add a bag/accessory/requested outerwear. This keeps arithmetic and identity reviewable. It is a heuristic, not exhaustive optimization or a trained fashion model.
- LlmGateway centralizes model deadlines, size/token/concurrency limits and strict-schema requests. UserQueryAi sees user text only. AiOutfitAdvisor sees original demo facts only, never third-party descriptions. Every proposed ID and total is revalidated, and current product facts are re-read before response.
- Import is serialized per application process and records run status in PostgreSQL. Scheduled refresh is opt-in and leaves stale historical rows intact but hidden. A single application instance is the supported MVP topology.

## Operational limits

Manual/periodic bounded keyword imports are not full inventory reconciliation. Distributed scheduling, crash recovery for RUNNING import records, provider-specific retention cleanup and production authentication/rate limiting are future work. Multi-retailer display and AI permissions require individual review. Live provider/model access, deployment and performance have not been verified. No usage metrics or merchant partnerships are claimed.

## Data model

products: identity, source, retailer, optional brand/category/slot/color/sizes/style/occasion facts, decimal price/currency, image/outbound URL, availability uncertainty, timestamps and last observation. Identity is unique by source/external ID. V1/V2 records are preserved.

catalog_import_runs: run ID, timestamps, RUNNING/SUCCEEDED/FAILED, counts and sanitized error type. Successful pages remain committed if later pages fail; run failure does not imply a global rollback.

Frontend responsibilities are split into catalog navigation, reusable ProductCard, native ProductDetail dialog and a conversational StylePanel. Demo illustrations are original backend-served SVGs; Vite proxies the same assets in development.
