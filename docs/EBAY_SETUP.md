# Legacy eBay integration — current code and historical activation

Reviewed for Closie Demo v1.0 on 2026-10-28. This integration is retained for private retail work; it is **not part of the public concept demo**. Public-demo mode disables notifications, imports and eBay display and rejects the callback/admin routes. Do not configure eBay credentials on that service. Use [PUBLIC_DEMO_DEPLOYMENT.md](PUBLIC_DEMO_DEPLOYMENT.md) for the current release.

## Current implementation

- An explicitly enabled GET `/api/ebay/account-deletion` computes SHA-256 over UTF-8 `challenge + verification token + exact configured endpoint` and returns the challenge response as JSON.
- POST validates ECC signatures against authenticated, fixed-host production eBay key retrieval. The verifier bounds input, rejects duplicate JSON keys and caches/paces key lookups. A header cannot choose an arbitrary key URL.
- A verified event is processed transactionally. Acknowledgement follows successful commit; notification-ID hashes deduplicate retries. The handler does not persist raw notification bodies or account identifiers.
- V4 introduced receipts/control state. V5 added HMAC seller ownership, deleted-account suppression and a stable hash-key fingerprint. Migrations preserve existing tables/records; processing a valid deletion event can intentionally delete matching cache rows.
- With seller tracking enabled and a valid seller username, `EbayAccountPolicy.deleteSeller` suppresses the username/aliases, deletes that seller's rows and conservatively removes legacy rows without ownership. Other known sellers remain. Ingestion checks suppression under the same control-row lock.
- Without a usable seller identity or tracking, the handler deletes all eBay cache rows and pauses imports with `IDENTITY_MISSING`. A signed test can follow either path depending on its payload/configuration; do not send tests against an unreviewed populated catalog.
- `POST /api/admin/catalog/resume-empty-legacy` requires the import token, explicit acknowledgement, tracking enabled, the stable key and an empty catalog in the specific `LEGACY_EMPTY_REVIEW` state. It cannot clear identity-related pauses. There is no generic pause bypass. Public-demo mode blocks this endpoint entirely.

Relevant code: `notification/EbayNotificationController`, `EbayNotificationVerifier`, `EbayDeletionProcessor`, `EbayAccountPolicy`, `service/ProductIngestionService` and `controller/CatalogAdminController` under `src/main/java/com/fashionsearch/`.

## Private setup, only after separate authorization

`python3 scripts/prepare-ebay-notifications.py` creates/reuses an owner-only token in ignored `.local/ebay-notification-token`. With `--endpoint https://YOUR-DOMAIN/api/ebay/account-deletion`, it prepares ignored `.env.local` settings while keeping notifications disabled. It does not print the token. Never commit or share credentials, tokens, screenshots of them or private callback payloads.

Production verification requires backend-only `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, an exact stable HTTPS endpoint and callback token. Seller tracking additionally requires a stable private 32-byte hex `EBAY_ACCOUNT_HASH_KEY`; changing it requires operator review. Sandbox credentials cannot authenticate production notification-key retrieval. Renaming the application does not itself require regenerating credentials.

The callback must reach the same backend/database as the retail cache. The retained Caddy file is an unexecuted reverse-proxy template, not the public-demo deployment path. Network rate limiting, backup/WAL retention, restore-time deletion replay, receipt retention, monitoring and provider permissions require a separate operational review before real commerce use.

## Historical evidence — 2026-10-27

The earlier retail prototype was deployed with authorization to Railway. The then-current checks recorded a successful HTTPS challenge, production OAuth/Browse responses and backend logs showing verified notifications reaching the processor; the user also reported a successful test notification. A direct receipt-table query was not completed. These are dated records from the earlier implementation, not a current deployment, uptime, inventory, account-permission or end-to-end compliance certification. The original blanket-purge behavior described in old reports is superseded by V5's seller-aware behavior above.

This PR preparation did not access provider accounts, send notifications, import products, resume imports or change cloud data. Current automated tests use generated keys, mocked responses and disposable databases.

References retained from implementation:

- [eBay marketplace account deletion guide](https://developer.ebay.com/develop/guides/sell/marketplace-user-account-deletion)
- [Official Java notification SDK](https://github.com/eBay/event-notification-java-sdk)
- [Official Node notification verifier](https://github.com/eBay/event-notification-nodejs-sdk/blob/main/lib/validator.js)
