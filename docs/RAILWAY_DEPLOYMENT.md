# Historical Railway retail deployment — 2026-10-27

**Archive, not deployment instructions for Closie Demo v1.0.** The current release has been rehearsed locally and has not been deployed by release/PR preparation. Follow [PUBLIC_DEMO_DEPLOYMENT.md](PUBLIC_DEMO_DEPLOYMENT.md) and explicitly select `railway.public-demo.toml` for a new, separately approved public-demo service.

## What the earlier report recorded

An authorized Railway trial deployment created a retail web service and an independent PostgreSQL database. React was served by Spring Boot on one origin. No local database was copied. The earlier checks recorded public HTTPS, an initially empty live catalog, callback challenge validation, unsigned-notification rejection and import-token enforcement. A later check recorded production eBay OAuth/Browse responses and signed-notification processing logs. Receipt rows were not independently queried. These observations are historical; current uptime, credit, inventory and configuration have not been rechecked in this PR task.

The original report's trial balance/expiry and provider-configuration statements were snapshots, not lasting guarantees. Operational project/service/environment identifiers and account-specific timestamps have been omitted from this distributable summary. This changes documentation only; no Railway resources or data were changed or removed.

## Compatibility and safety boundary

- The old `railway` profile is for the retained retail backend. It enables seller tracking and requires its stable private hash key. It is not the current concept-demo profile.
- The root Dockerfile now defaults to `public-demo`. Rebuilding an old service without reviewing its profile/database could fail the dedicated-database startup guard or select the wrong experience. Do not attach this branch to an existing auto-deploy service.
- Public-demo hosting uses only `public-demo`, a dedicated `closie_public_demo` database, healthcheck `/api/studio/status`, bundled original images and no model/eBay/import keys.
- `scripts/deploy-railway.py` is a retained **legacy** upload helper targeting the earlier service by fixed resource IDs. It does not select the public-demo config and its source whitelist omits `frontend/public`, now required by the Dockerfile. **Do not run it for this release.** It remains preserved for historical context; no cleanup/deletion or deployment was authorized here.
- Use an independent durable pgvector-compatible database, private networking and reviewed backups for the new demo. Never reset or reuse a private/live database to satisfy the new profile.
- Historical notification policy descriptions are superseded by the current seller-scoped/fail-closed behavior in [EBAY_SETUP.md](EBAY_SETUP.md). Public-demo mode blocks those routes.

The account's current plan, existing services, remaining credit, GitHub auto-deploy connections and workspace spending controls must be checked before a later approved deployment. No hosting upgrade, resource creation, source upload or billing change is authorized by PR preparation.
