# Closie repository instructions

These instructions apply to this entire repository.

- Preserve Discover → Find → Style, the California campus visual identity, and the Java 21 / Spring Boot / PostgreSQL / React architecture.
- Read `PROGRESS.md`, the current README and the relevant spec before changing behavior. Historical verification reports are dated evidence, not current results.
- Public demo mode is non-billable. Keep server-side guards in the filter, job worker and all model gateways. Never rely only on hidden buttons or missing keys.
- Do not run paid APIs without explicit user authorization and a stated budget. Demo release preparation makes no paid calls.
- Never commit secrets, `.env.local`, `.local`, private browser capabilities, uploads or database dumps. Provider keys must not enter frontend process environments or bundles.
- Keep FashionConcept, RetailProduct and OwnedItem distinct. Never invent commerce facts or claim pre-generated assets are fresh AI responses.
- Use additive migrations; never edit an applied migration, drop databases or remove existing data to repair tests.
- Use `closie_public_demo` only for the public demo; preserve private `closie_studio`, `closie_demo` and `fashion_search` databases.
- Anonymous demo data uses browser-scoped opaque capabilities. Preserve ownership checks, anchor locking, immutable samples and bounded storage.
- Local modes: `./dev public-demo` (5176 / 8082); `./dev studio` (5173 / 8080); `./dev stop-public` / `./dev stop` preserve database data.
- Run `./mvnw test` and `cd frontend && pnpm build`. For full browser verification, run Maven with `CLOSIE_BROWSER_SMOKE=true`; Testcontainers must use disposable databases.
- Public browser tests use `playwright.public.config.js`; private studio and retail suites remain separate. Mocked model tests do not prove live API access.
- Document observed results and limitations. Do not claim deployment, quality evaluation, retailer partnerships or usage metrics without evidence.
- Do not push, merge, publish, purchase hosting or change production resources without explicit authorization.
