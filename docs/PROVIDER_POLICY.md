# Product source review and AI boundary

Reviewed public documentation on 2026-10-24. This is an engineering checklist, not evidence of account-specific authorization. No retailer is integrated merely because its name appears in product strategy.

## eBay

Primary sources:
- https://developer.ebay.com/join/api-license-agreement
- https://developer.ebay.com/api-docs/buy/buy-requirements.html
- https://developer.ebay.com/api-docs/buy/static/ref-buy-browse-filters.html

The agreement requires displayed listing information to be within six hours of eBay's information. It also addresses attribution, licensing, restrictions on training AI with eBay content, and additional restrictions on Restricted APIs. These provisions are not interchangeable: the Restricted APIs definition is specific. No claim is made that Browse content has blanket permission for third-party AI inference or embeddings. Account agreements and intended use still require review.

**Default behavior:** `EBAY_DISPLAY_ENABLED=false`. Import remains an authenticated development action. Legacy records are preserved but cannot be displayed without `last_seen_at`. If display is explicitly enabled after account/use-case review, the API only returns eBay rows observed within six hours. Expired rows remain in storage but are excluded from every user-facing read, search, and outfit selection. A timestamped observation is not an inventory/size guarantee. The UI says to check availability with the retailer. An opt-in scheduled refresh now repeats bounded imports (four hours by default). This is not exhaustive inventory reconciliation; expired records remain hidden. Search-page inclusion is evidence of observation only, so availability stays UNKNOWN.

The fixed-price filter is retained for backwards compatibility. eBay documents leaf-category considerations. Before production ingestion, confirm marketplace leaf categories and response behavior with authorized credentials. The localhost HTTP test verifies transport, authentication request shape, host resolution, and single encoding; it does not verify eBay account permissions or real API responses.

Source attribution and an outbound product link appear on every card. Images remain remote provider URLs (no image copying/proxying). Review image-display, attribution and allowed caching terms before enabling display. Sandbox listings must never be represented as live inventory. No credentials or account-specific permissions were available during implementation.

## Other retailers and affiliate feeds

`ProductSource` and `SourceProduct` form the adapter boundary. `EbayProductSource` is the only production adapter. A future adapter must document:
1. Authorization and the actual feed/API agreement; no protected scraping.
2. Permitted storage, display, freshness, image use and attribution.
3. Any permitted AI inference/embedding processing, separately from training.
4. Required URL/affiliate handling, currency, stock semantics and deletion rules.

Non-eBay sources are excluded by default. `CLOSIE_PERMITTED_SOURCES` is an explicit allowlist of reviewed adapter source IDs, not proof of a license. Implement the source-specific refresh/expiry policy before adding a source to the allowlist. No Aritzia, Zara, Mango, or independent-label feed is included. User-entered brand-inspired vibes are not merchant integrations or asserted partnerships. Brand is null unless supplied by an authorized provider; the application never guesses it from a listing title. Color and sizes are also nullable provider facts. Outfit-slot classification from title/category is a heuristic, not a provider assertion.

## AI processing

The query interpreter receives only the user's request and previous structured preferences. A separate outfit advisor may receive a small shortlist of original Closie demo facts, never third-party catalog content. Both use structured schemas. All displayed products and prices come from PostgreSQL. Explicit filters override interpreted constraints. Missing keys/model access, timeouts, HTTP errors, malformed output and invalid preferences fall back to deterministic rules.

No third-party catalog content is sent to AI, even when enabled. Only original fictional sample metadata is permitted for model outfit selection/explanation. eBay and all other external sources are explicitly excluded in code. Explanations for external catalogs remain deterministic. Model access requires `OPENAI_MODEL`, `OPENAI_API_KEY`, and explicit `CLOSIE_AI_ENABLED=true`. The UI discloses rules-based versus AI-assisted interpretation. No embeddings or AI training are implemented.

OpenAI interface reference: https://developers.openai.com/api/docs/guides/structured-outputs

## Original sample collection

`closie-demo` contains application-authored fictional product facts and original SVG illustrations, with fictional sample studio names and prices. It is not a retailer or a representation of inventory. There are no purchase links. It is visible only in explicit demo mode, in the isolated local `closie_demo` database, and cannot be enabled through the live source allowlist. Browser saves use a separate demo namespace. Only this application-owned sample content may be included in the outfit-model shortlist in this release.

## Verification update — 2026-10-26

No live provider/model credentials were available. Additional localhost/mock tests cover token renewal, transient retries, cooldown, provider-aspect mapping and model request/response handling. These do not verify production access, caching permission, image-display rights or actual model output quality.
