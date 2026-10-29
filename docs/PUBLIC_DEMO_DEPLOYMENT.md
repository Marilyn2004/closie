# Public demo deployment preparation

No services were provisioned, purchased, deployed or modified during preparation. These are instructions for a later approved deployment.

## Recommended architecture

Use one Railway Docker service to serve the built React/Vite frontend and Spring Boot API on the same origin, plus a **dedicated PostgreSQL database named `closie_public_demo`**. The current migrations need `pgvector`; use `pgvector/pgvector:pg17` with a durable PostgreSQL volume or a compatible managed service.

All seven public images are immutable original assets inside the application artifact. They are rebuilt/redeployed with the code and do not require an upload volume. Public uploads and every model endpoint are disabled. Saved preferences and outfit boards live in PostgreSQL and survive application restarts/redeployments if the database volume is retained.

This avoids a separate frontend host, CORS configuration and a runtime image bucket for the public demo. Private local generated images are **not** copied to cloud storage automatically. A future hosted private mode would require a persistent asset volume/object-store adapter and a separate access policy.

Railway is practical given the existing account, but not guaranteed free: its published Hobby plan is $5/month with $5 included resource usage, and usage above that is extra. Its Free plan has $1 monthly credit and limited resources; do not assume that is enough for Java plus PostgreSQL. Review available credits and a hosting budget before approval. See [official plans](https://docs.railway.com/pricing/plans) and [persistent volumes](https://docs.railway.com/volumes/reference). No hosting budget is charged to the $3 model allocation.

An alternative is the same backend container with a dedicated Neon database; Neon supports [pgvector](https://neon.com/docs/extensions/pgvector). Its free limits, connection pooling and cold-start behavior require a separate check against the chosen plan. Do not silently move an existing database.

## Local production-artifact rehearsal

Prerequisites: Docker/Colima. Choose a disposable local password privately in the shell, without committing it.

```sh
export PUBLIC_DEMO_DB_PASSWORD='YOUR_LOCAL_REHEARSAL_PASSWORD'
docker compose -p closie-public-demo -f docker-compose.public-demo.yml up --build -d
```

Visit http://127.0.0.1:8083. This starts a separate named database volume with no public database port. It does not connect to the private local database or existing production services.

```sh
docker compose -p closie-public-demo -f docker-compose.public-demo.yml down
```

Do not add `-v`: the database volume contains saved demo state. Back it up before any infrastructure replacement. An application redeploy needs the same database and volume; changing the image alone must not delete data.

The current machine did not have the Compose plugin. The Dockerfile build and equivalent `docker run` web/database topology were actually verified locally; this exact Compose command was not executed. Install/use Compose only if choosing that rehearsal path. The smoke scripts can target an already running local artifact without Compose.

For the lighter source-development preview use `./dev public-demo` at http://127.0.0.1:5176, backend 8082. It creates only the dedicated local `closie_public_demo` database on 55432; `./dev stop-public` leaves it intact.

## Railway steps — only after approval

1. Choose a new public-demo service/project/environment; do not attach this branch to an existing auto-deploying production service.
2. Provision a separate durable PostgreSQL resource. Create the dedicated `closie_public_demo` database and ensure the migration user can enable `vector`. Set up a database backup policy.
3. Build the root Dockerfile. Select `railway.public-demo.toml` as the service's configuration path; it configures the Docker builder, healthcheck and bounded restart policy. See [config-as-code reference](https://docs.railway.com/config-as-code/reference).
4. Configure only the names in `ops/public-demo.env.example`: `SPRING_PROFILES_ACTIVE=public-demo`, JDBC `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Railway supplies `PORT`. Do not use PostgreSQL's `postgres://` URL as a JDBC URL without converting its format.
5. Do not supply OpenAI/Gemini/eBay/import credentials. Even if provider keys or enable flags accidentally exist, the public policy must remain active. Never combine the public-demo and studio profiles.
6. Flyway applies additive migrations on startup. The catalog seeder preserves existing IDs/rows and adds missing public samples idempotently.
7. Healthcheck `GET /api/studio/status` must return publicDemo=true, imageConfigured=false, visionConfigured=false and interpretation=SAVED_CONCEPT_SEARCH.
8. Test generated HTML/JS/assets, favicon, concept images, search, favorites, outfit replacement and mobile views under the assigned HTTPS origin.
9. Direct POST requests to image generation, doll, upload, owned-item, import and legacy AI endpoints must return 403. Budget/admin/debug routes must be inaccessible. No provider request or spend reservation should occur.
10. Save a look/preference, restart only the app and verify persistence. Rebuild/redeploy the same commit and repeat. Inspect database backups before deleting any old resources.

## Rollback and release boundaries

Keep the prior application artifact and a PostgreSQL backup. Roll back the application image against the preserved database; do not automatically down-migrate or drop tables. Avoid exposing the private live-generation profile as a rollback shortcut.

The public demo deliberately has no live model claims, private item recognition or shopping availability. No paid calls are needed for the approval checklist. Cloud deployment, HTTPS and remote CI execution cannot be claimed from local test results.
