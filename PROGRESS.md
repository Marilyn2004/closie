# Closie — Demo v1.0 status

Current release implementation: `03c3af0` on `closie-concept-mvp`, based on verified commit `8143fa9`. Deployment remains unapproved and has not been performed.

## Implemented

- Public-demo Spring profile, dedicated database guard, explicit HTTP route allowlist and model/job-layer safety guards.
- Existing-concept Find search, including garment/color constraints, empty states and contextual refinement; no new images or designs claimed.
- Seven original pre-generated garment images, including the single previously verified OpenAI output. Four covered categories: tops, bottoms, shoes, bags.
- Two immutable sample outfit boards, copy-on-edit single-piece replacement, favorites for concepts and outfits, style collections and browser-scoped preferences.
- Honest public limitations for uploads, photo recognition, retailer lookup, live edits and doll previews; private local tools retained.
- Same-origin Docker build and separate deployment/rehearsal configuration. Bundled demo images do not rely on an ephemeral server disk.

## Verification

- 152 backend/JUnit items passed; public, private and legacy browser suites passed (14 journeys).
- Production frontend build and full Docker image build passed.
- Packaged app starts without model keys; direct paid/admin routes return 403; model-spend and OwnedItem rows remain zero in the demo DB.
- Favorites, outfit IDs and all seven bundled assets survived an app restart and a fresh app container on the retained PostgreSQL database.
- Desktop/mobile public screenshots were visually inspected. Detailed evidence and current release decision are in `docs/PUBLIC_DEMO_RELEASE.md`.
- Tracked Git history scan: 10 existing commits / 279 unique blobs, no recognized credential patterns; only `.env.example` is tracked. This is a bounded pattern scan, not a comprehensive security certification.

Historical evidence: one OpenAI image request succeeded on 2026-10-28, estimated $0.00684; that test is not repeated for this release. See `docs/OPENAI_IMAGES.md`.

## Remaining limits and next decision

- Small curated capsule: no dress/layer/jewelry samples; only one shoe design and one bag. Do not claim broad catalog coverage.
- Preferences/saves use a browser capability, not a login or cross-device sync account. Public uploads are disabled.
- Hosted HTTPS, resource costs, backup/restore and actual cloud behavior remain unverified until an approved deployment.
- Existing private assets still need durable volume/object storage if private generation is ever hosted. The public demo uses only bundled images.
- Source publication still requires the owner's license choice; no public repository publication is performed.
- Next step after successful local release checks: user approval of the target hosting/cost and a separate public-demo deployment. Do not repurpose the existing production service.

## First PR preparation — 2026-10-28

On incoming HEAD `1c1b784`, a documentation-only cleanup reconciled historical retail/eBay/Railway reports with the shipped public demo, removed current-doc local paths, and documented compatibility, licensing and publication limits. No runtime code, migrations, user data or tracked image changed. Full local regression reran successfully: 152 JUnit items (0 failures/errors/skips), 14 Playwright cases and frontend production build. See `docs/PR_PREPARATION.md` and `docs/PR_DRAFT.md` for scope/evidence and the proposed PR. The existing GitHub repository is public; push/PR creation, merge and cloud deployment still require separate explicit approval.
