# Proposed title

feat: deliver Closie Demo v1.0 with Discover → Find → Style and guarded AI integrations

# Proposed description

## Product and outcome

The original repository provided a backend retail-ingestion foundation. This first PR delivers Closie — Your Personal AI Stylist — as a California-campus fashion concept and styling portfolio demo. Discover → Find → Style now works over a curated capsule without requiring paid APIs or presenting generated concepts as real inventory.

## Features

- Seven original pre-generated concepts, four garment categories, five style collections and explicit preference-based ranking.
- Natural-language keyword/structured retrieval with garment/color constraints, contextual refinement and honest empty results.
- Two immutable sample outfits, anchor-locked styling boards, copy-on-edit complementary replacements, favorites, Saved and browser-scoped preferences.
- Preserve the separate local private studio: structured fashion briefs, image generation/edit provider interface, OpenAI and optional Gemini adapters, uploads and optional doll-preview plumbing. Public mode disables these live tools.

## Architecture and compatibility

React 19/Vite is bundled into one Java 21/Spring Boot Docker service, backed by PostgreSQL/pgvector and Flyway. V1 is unchanged; V2–V8 extend retail, notification and studio storage. Bundled public images survive app replacement; saved state requires a durable database. Existing retail ingestion/search/styling and seller-aware deletion are retained separately.

Local PostgreSQL defaults change to loopback port 55432. The Docker image defaults to public-demo with a dedicated closie_public_demo database. This is not a drop-in redeploy of the old retail service. The legacy Railway upload helper is not suitable for this release; follow docs/PUBLIC_DEMO_DEPLOYMENT.md. No existing user database, volume or private asset is removed or migrated to cloud.

## Public security and API cost protection

Public-demo profile protection, dedicated-database startup checks, HTTP allowlisting and independent model/job guards block paid generation, vision, uploads, retail import and administrative/private routes, even with fake keys and unsafe enable flags in tests. No OpenAI/Gemini/eBay key is required. Inputs and document/revision storage are bounded; browser capabilities are hashed server-side and shared examples cannot be edited in place. Private OpenAI use retains request caching, durable spend reservations and explicit limited retries; an app ledger is not a provider-enforced invoice cap.

Historical credentials/configuration are excluded from tracked files. The focused history scan found no recognized credential patterns; it is not a security certification. Generated-asset provenance is documented. Source and asset license choices remain with the owner.

## Validation

- Full local Maven run: 152 JUnit items passed, zero failures/errors/skips, including opt-in browser launchers.
- 14 Playwright cases passed: public 4, private studio 5, retail samples 3, legacy catalog 2.
- Production frontend build passed. Initial browser launch failures from missing Node on PATH were resolved in the test environment; no application/test change was required.
- Previous release checks verified the packaged Docker app without model keys, blocked privileged routes, bundled images and persistence across app restart/recreation. Those artifact checks were not repeated for this docs-only cleanup.
- Zero new paid AI calls. Remote GitHub CI and hosted HTTPS are not yet verified for this release.

## Known limitations

Small four-category capsule; only one shoe and bag design. Search/styling use bounded rules, not embeddings or a trained fashion model. No public live generation, photo recognition, retailer availability, checkout, accounts or cross-device sync. Private edit/doll quality and broader model evaluation remain unverified. Anonymous saves depend on the browser capability; production-grade per-IP rate limiting is future work. Historical docs are marked; public repository history still includes some local-path metadata. No LICENSE has been chosen.

## Deployment plan

Separate approval required. Use a new single-replica Railway Docker service plus an independent durable pgvector PostgreSQL database, public-demo profile, private DB networking, no provider keys and railway.public-demo.toml. Initial recommendations: app 512 MiB / 1 vCPU cap, DB 256 MiB / 0.5 vCPU cap, 1 GB persistent database volume. Estimated low-traffic Hobby total $5–8/month, subject to actual usage and other workspace services. Review spending alerts/limits and backups first; then verify HTTPS, direct API denials, saves and restart persistence after an authorized deployment. Do not auto-deploy PR previews or overwrite the old service.

This PR does not authorize deployment, paid API use or a merge. See docs/PR_PREPARATION.md for review evidence and docs/PUBLIC_DEMO_RELEASE.md for earlier release checks.
