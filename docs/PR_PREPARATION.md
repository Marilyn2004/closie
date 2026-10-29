# First GitHub PR preparation — 2026-10-28

## Scope and Git baseline

Branch `closie-concept-mvp`; incoming HEAD `1c1b7845c4ba8927a1bbd55e19c8ff9aeb6065ca`. Read-only remote queries confirmed default/main `e298ffd7628a3a646bf0b708272a7816b6560364`, matching local `origin/main`, and no remote `closie-concept-mvp` branch at review time. The branch contained 11 commits above main before this documentation cleanup. No fetch, history rewrite, push, PR creation, merge or deployment was performed.

The unauthenticated GitHub repository API reported the repository already public and no selected license. A later push publishes all reachable branch history, not only the cleanup commit. Some historical blobs retain the author's local filesystem path; current documentation uses repository-relative placeholders. No history rewrite was authorized. The existing GitHub description still describes semantic retrieval, which this demo does not implement; update it separately only after owner approval.

## Existing uncommitted documents

- `VERIFICATION.md`: retained as dated retail evidence, with explicit current-release links and superseded-policy notes. Historical runtime configuration is not a Git credential commit.
- `EBAY_SETUP.md`: retained and corrected against V5, `EbayAccountPolicy`, `EbayDeletionProcessor` and the authenticated narrow resume endpoint. Seller-scoped deletion and missing-identity purge/pause are distinct. Not public-demo setup.
- `RAILWAY_DEPLOYMENT.md`: retained as an archive; account-specific resource IDs, unrelated project names and expired trial figures are omitted. Current hosting instructions point to PUBLIC_DEMO_DEPLOYMENT.md. No assertion about current cloud state.

Original versions of these three documents remain in ignored `.local/pr-preparation/original-documents/`; no user files or data were deleted. The cleanup adds them to Git only after sanitizing/reconciling their content.

## Diff review and publication scan

The branch-to-main change contains the retail foundation, private studio, public demo, tests, migrations, build/CI files, assets and historical docs. It is a large first PR (189 changed paths before cleanup); retained retail functionality/specs are labeled rather than removed. No unrelated database dumps, node_modules, generated frontend build, local uploads, environment secrets or private browser capabilities are tracked. Runtime code and migrations were not changed by this cleanup.

Review covered the diff inventory, configuration/build/CI, public policy/filter/database guards, owner-scoped storage/controllers, search/styling, job/provider/spend controls, retail ingestion/deletion boundaries and automated coverage. V1 is unchanged; V2–V8 are additive migrations. Existing databases/volumes must not be reset. Public routes block model/upload/retail/admin access; provider and worker guards add independent protection. Inputs/storage are bounded, but per-IP rate limiting and production authentication are not implemented. This is a focused release review, not a penetration test or exhaustive correctness/security certification.

A bounded pattern scan read 322 unique Git blobs across the 11 branch commits and the main snapshot, plus the three initial working documents. It found no recognized provider keys, private-key blocks or credential-bearing URLs. Local filesystem paths were found in historical documentation. Only `.env.example` is a tracked environment file, with blank provider keys and explicitly local example database credentials. Pattern scanning cannot prove absence of every possible secret format.

Seven original generated concept images have provenance in CONCEPT_ASSETS.md; retail SVGs are authored fixtures. No retailer photos are bundled into the public capsule. Current public Discover screenshot was visually rechecked; existing release inspection covers all assets. Source/asset license choice and human aesthetic acceptance remain owner decisions; no license or legal clearance is invented. Dependency manifests/lockfiles are retained, not a legal audit of all transitive packages.

Compatibility notes: local DB defaults now use loopback 55432 instead of 5432; Docker defaults to `public-demo`, requiring `closie_public_demo`. Existing retail deployments must explicitly review their profile and stable seller hash key. The old upload helper has fixed legacy service IDs and omits the required frontend/public directory; it is preserved but explicitly prohibited for this release in deployment docs. Do not wire this branch to an existing auto-deploy service.

## Validation rerun

- Java 21 full Maven run with `CLOSIE_BROWSER_SMOKE=true`: **152 JUnit items, 0 failures, 0 errors, 0 skipped**.
- Playwright launched against disposable Testcontainers backends: **14 browser cases passed** (public 4, private studio 5, retail samples 3, legacy catalog 2).
- Production frontend build: passed (Vite 7.3.7).
- The first test attempt had four browser-launcher failures because Node was absent from child-shell PATH. Repeating with the installed Node on PATH passed the full suite; no test or application change was needed. README now states both PATH and CLOSIE_NODE requirements.
- Browser tests refreshed a tracked screenshot. Its prior committed bytes were restored; the new test rendering is retained only in ignored .local/pr-preparation, avoiding an unrelated binary diff.
- Markdown local-file links, Python/TOML syntax, `bash -n dev` and `git diff --check`: passed. Broken relative links in the archived retail README were corrected.
- Maven output: local `/tmp/closie-pr-validation.log`; per-suite reports/browser logs in ignored target/. No paid provider requests, production DB access or data cleanup occurred.
- Earlier packaged Docker/restart checks remain dated evidence in PUBLIC_DEMO_RELEASE.md; the image was not rebuilt for this documentation-only cleanup. Remote GitHub CI has not run for these commits.

## Next gates

Review the proposed body in PR_DRAFT.md and the cleanup commit. Before a push, explicitly approve publication to the existing public repository, including historical local-path metadata and the unresolved license choice. No credential rotation is indicated by this bounded scan. Do not claim broad catalog coverage, semantic search, public live AI, merchant access or cloud verification. After a separately approved push, open a draft PR to main and inspect CI before any merge decision. Hosting remains a separate approval with the previously assessed $5–8/month estimate and dedicated database/backup setup.
