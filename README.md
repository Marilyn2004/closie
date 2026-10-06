# Fashion Search Engine

Milestone 1 is the backend and database foundation. A Spring Boot application stores product records in PostgreSQL through Spring Data JPA. Flyway creates the schema and enables the pgvector extension. The embedding column is added in a later milestone, when the embedding model is chosen.

## Requirements

- Java 21
- Docker, for the local database and for tests

## Local database

```bash
docker compose up -d
```

PostgreSQL listens on port 5432. The database is `fashion_search`, the user is `fashion`, and the password is `fashion`.

Override the connection with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

## Tests

```bash
./mvnw test
```

Tests start their own `pgvector/pgvector:pg17` container. They do not use the Compose database.

## Run the application

```bash
./mvnw spring-boot:run
```

On startup, Flyway enables pgvector and creates the `products` table.

## Import products

Import is a development action. It fills the local catalog from the eBay Browse API. User search is not implemented yet, and it will not call eBay.

Set these environment variables. Leave them out of Git:

```text
EBAY_CLIENT_ID
EBAY_CLIENT_SECRET
IMPORT_TOKEN
```

Blank names are listed in `.env.example`. With the database and application running:

```bash
curl -X POST http://localhost:8080/api/admin/products/import \
  -H "X-Import-Token: $IMPORT_TOKEN"
```

One import searches the US marketplace for four fashion keywords, requests fixed-price listings with `buyingOptions:{FIXED_PRICE}`, and stores at most two pages of 50 items per keyword. Running it again updates an existing listing when its title, price, or other stored fields change. eBay's filter reference says this buying-option filter is defined at leaf-category level. This import does not send a category id, because those ids still need to be confirmed for the US marketplace.
