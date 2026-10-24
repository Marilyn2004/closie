# Closie V2 architecture

React/Vite → same-origin `/api` proxy → Spring MVC → QueryInterpreter / CatalogService / OutfitService → JPA Specifications → PostgreSQL.

Authenticated import → ProductIngestionService → ProductSource adapter → provider HTTP API → normalization → page transaction → catalog upsert. EbayProductSource adapts the existing tested eBay client. SourceProduct carries nullable provider facts, independent of eBay DTOs. Future feeds implement the adapter rather than a new search engine.

## Invariants

- Product identity remains `(source, external_id)`.
- V2 adds fields and indexes without changing V1 or deleting existing data.
- Missing metadata is unknown; outfit slot classification is heuristic.
- Provider display permission and freshness apply to discover, search, detail and outfit candidates.
- User query interpretation can suggest preferences, never product records or prices.
- SQL enforces currency, min/max price, brand, color, category and keyword restrictions.
- Explicit UI filters win over natural-language interpretation.
- Style combines known clothing slots with shoes, retains an optional anchor, avoids duplicate IDs and enforces item-subtotal budgets using BigDecimal.
- No browser API keys, no catalog-to-LLM calls, no fabricated production catalog.

## Tradeoffs and limits

No vector index, queue, service split or separate search engine is necessary for this release. PostgreSQL keyword matching is deliberately inspectable. Pagination is offset-based; relevance is transparent preference-term matching, not personalized machine learning. Future work: query evaluation sets, taxonomy/negation handling, scheduled provider refresh and end-of-listing reconciliation, source-specific policy configuration, import run records, concurrent-upsert handling, deployment authentication/rate limits and observability. API request context is supplied by the browser and revalidated; it carries no privileged state.

The main checkout remains on main. Work happens on branch `closie-v2` in a separate worktree. Repository/artifact/package/database names were preserved; only product-facing identity is Closie. No remote changes or production-resource rename is part of this implementation.
