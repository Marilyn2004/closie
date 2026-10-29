> Historical V1 specification. Closie V2 product direction and implementation are documented in README.md and docs/ARCHITECTURE.md; those supersede conflicting scope/non-goals below.

# AI Fashion Search Engine

## 1. Project Overview

An AI-powered fashion search engine that allows users to describe clothing
in natural language and retrieves multiple relevant real-world products,
ranked by semantic similarity and filtered by structured constraints such
as price and category.

Instead of requiring users to know exact product names or fashion terminology,
the system allows users to search using natural descriptions.

Example query:

> "black cropped leather jacket, fitted and minimal, under $200"

The system should understand the user's description, retrieve multiple matching
products from the product catalog, rank them by relevance, and present the
results for the user to browse and choose from.

The AI assists with search and retrieval. It does not select a single product
or make the purchasing decision for the user.

---

## 2. Problem

Traditional fashion search relies heavily on exact keywords.

Users may know what style they want but not know the correct product name,
fashion terminology, or brand.

For example, a user may search:

> "cream cardigan, slightly oversized, cropped, under $100"

A relevant product might instead be described as:

> "Ivory ribbed knit sweater with a relaxed silhouette and waist-length cut"

Traditional keyword matching may fail even though the product is semantically
similar to what the user wants.

This project combines natural-language understanding, embeddings, vector
similarity search, and structured filtering to improve fashion product
discovery.

---

## 3. Core User Flow

The primary user flow is:

1. User opens the web application.
2. User describes the clothing they want in natural language.
3. The backend interprets the query.
4. Structured constraints such as price and category are extracted.
5. The semantic portion of the query is converted into an embedding.
6. The system searches the local product catalog using vector similarity.
7. Structured filters are applied.
8. Matching products are ranked by relevance.
9. Multiple real products are returned.
10. The user browses the results and chooses which products to explore.

Example:

User query:

> "soft feminine cream cardigan, slightly cropped, under $100"

Possible results:

- Product A — $79
- Product B — $65
- Product C — $89
- Product D — $72
- ...

The system should return a collection of relevant products rather than one
AI-selected recommendation.

---

## 4. MVP Features

### 4.1 Natural-Language Search

Users can describe clothing using free-form natural language.

Examples:

> "minimal black leather jacket under $200"

> "cream cropped cardigan, slightly oversized, under $100"

> "simple black dress for dinner, not too formal"

The user should not need to know exact product names.

---

### 4.2 Query Understanding

The system separates semantic meaning from structured constraints.

Example input:

> "fitted black cropped leather jacket under $200"

Possible internal representation:

```json
{
  "semanticQuery": "fitted black cropped leather jacket",
  "category": "jacket",
  "maxPrice": 200
}
```

The semantic query is used for vector search.

Structured values such as price and category are used as database filters.

If both the natural-language query and explicit UI filters specify the same
constraint, the explicit UI filter takes precedence.

---

### 4.3 Semantic Product Retrieval

The system generates an embedding for the semantic portion of the user's query.

The query embedding is compared against product embeddings stored in
PostgreSQL using pgvector.

Products with semantically similar descriptions should rank higher even when
they do not contain the exact words used by the user.

---

### 4.4 Structured Filtering

The MVP supports:

- Maximum price
- Product category

Structured filters work together with semantic similarity search.

Example:

Semantic requirement:

> "minimal cropped black leather jacket"

Structured requirements:

```text
category = jacket
price <= 200
```

---

### 4.5 Ranked Search Results

Each search returns multiple matching products.

Products are ranked by semantic similarity while respecting structured
constraints.

The backend returns the top K results instead of selecting a single
recommendation.

For the MVP:

```text
Default result limit: 20 products
```

The frontend may initially display a subset of the results and allow the user
to browse additional results.

Raw vector similarity scores do not need to be displayed as user-facing
"match percentages."

---

### 4.6 Real Product Data

Products are imported from the eBay Browse API.

Each product should contain at least:

- Product title
- Product description when available
- Category
- Price
- Currency
- Image URL
- Original product URL
- Source
- External product ID

The external API acts as a product data source.

The application's own search engine operates against the local product catalog.

---

### 4.7 Product Results UI

Each search result should be displayed as a product card containing:

- Product image
- Product title
- Price
- Source
- Link to the original product page

Example:

```text
┌──────────────────────────┐
│                          │
│      PRODUCT IMAGE       │
│                          │
├──────────────────────────┤
│ Black Cropped Jacket     │
│ $129                     │
│ eBay                     │
│                          │
│ View Product →           │
└──────────────────────────┘
```

The user chooses which products to explore.

---

## 5. MVP Non-Goals

Version 1 will NOT include:

- User accounts
- Login/authentication
- Favorites
- Personalized recommendations
- Search history
- Image-based search
- Image uploads
- Multi-turn chatbot
- Conversational memory
- Mobile application
- Custom retailer web crawlers
- Multiple product providers
- Purchasing or checkout inside the application

These features may be considered after the MVP is complete.

The priority is to build a working end-to-end semantic fashion search engine
before expanding the feature set.

---

## 6. Technology Stack

### Backend

- Java 21
- Spring Boot
- Spring Data JPA
- Maven

### Frontend

- React

### Database

- PostgreSQL
- pgvector

PostgreSQL stores both structured product metadata and vector embeddings.

### AI

OpenAI API

Used for:

- Query understanding
- Text embeddings

### Product Data Source

eBay Browse API

### Development

- Git
- GitHub
- IntelliJ IDEA / Cursor

---

## 7. High-Level Architecture

```text
                         USER
                           │
                           ▼
                  ┌─────────────────┐
                  │ React Frontend  │
                  └────────┬────────┘
                           │
                         REST
                           │
                           ▼
                  ┌─────────────────┐
                  │   Spring Boot   │
                  │     Backend     │
                  └────────┬────────┘
                           │
             ┌─────────────┼─────────────┐
             │             │             │
             ▼             ▼             ▼
          OpenAI        Search         eBay API
           API          Service          │
             │             │             ▼
             │             │      Product Ingestion
             │             │             │
             └─────────────┼─────────────┘
                           │
                           ▼
                 ┌──────────────────┐
                 │   PostgreSQL     │
                 │   + pgvector     │
                 ├──────────────────┤
                 │ Product metadata │
                 │ Embeddings       │
                 └────────┬─────────┘
                          │
                          ▼
                 Similarity Search
                         +
                 Structured Filters
                         +
                       Ranking
                          │
                          ▼
                    Top K Results
                          │
                          ▼
                    React UI
```

---

## 8. Product Data Model

### Product

```text
id              Long
externalId      String
title           String
description     String
category        String
price           BigDecimal
currency        String
imageUrl        String
productUrl      String
source          String
embedding       Vector
createdAt       Timestamp
updatedAt       Timestamp
```

`externalId + source` should uniquely identify an imported product.

This prevents duplicate imports of the same external product.

---

## 9. Product Ingestion Pipeline

Product ingestion is separate from user search.

```text
eBay Browse API
        │
        ▼
Fetch fashion products
        │
        ▼
Normalize external product data
        │
        ▼
Create internal Product representation
        │
        ▼
Build embedding text
        │
        ▼
OpenAI Embeddings API
        │
        ▼
Product embedding
        │
        ▼
Store metadata + embedding
        │
        ▼
PostgreSQL + pgvector
```

Possible embedding text can combine:

```text
title + description + category
```

The ingestion process should avoid duplicate products using the external
product ID.

For the MVP, ingestion may be manually triggered during development.

The application should not call the eBay API for every user search.

---

## 10. Search Pipeline

Example query:

> "I want a fitted black cropped leather jacket under $200"

### Step 1 — Receive Query

```text
Natural-language query
+
optional explicit UI filters
```

### Step 2 — Query Understanding

The system extracts structured constraints while preserving the semantic
fashion description.

Example:

```json
{
  "semanticQuery": "fitted black cropped leather jacket",
  "category": "jacket",
  "maxPrice": 200
}
```

### Step 3 — Generate Query Embedding

```text
semanticQuery
      │
      ▼
OpenAI Embeddings API
      │
      ▼
Query vector
```

### Step 4 — Retrieve Candidates

Use pgvector to compare the query embedding against stored product embeddings.

Apply structured constraints such as:

```text
price <= 200
category = jacket
```

### Step 5 — Rank Results

Products are ordered by semantic similarity.

### Step 6 — Return Top K

For the MVP:

```text
K = 20
```

### Step 7 — Display Results

The React frontend renders the returned products as product cards.

The user browses the results and decides which products to explore.

---

## 11. Initial Backend API

### Search Products

```http
GET /api/search
```

Example:

```text
/api/search?q=black+cropped+leather+jacket&maxPrice=200
```

Possible parameters:

```text
q
maxPrice
category
limit
```

Default:

```text
limit = 20
```

Returns an ordered collection of matching products.

---

### Get Product

```http
GET /api/products/{id}
```

Returns details for one product.

---

### Import Products

```http
POST /api/admin/products/import
```

Triggers product ingestion during development.

This endpoint exists primarily for MVP development and should not be publicly
exposed without protection.

---

## 12. Suggested Backend Structure

```text
controller/
    SearchController
    ProductController
    ProductImportController

service/
    SearchService
    ProductService
    ProductIngestionService
    EmbeddingService
    QueryUnderstandingService

repository/
    ProductRepository

client/
    EbayClient
    OpenAIClient

model/
    Product

dto/
    SearchRequest
    SearchResult
    ParsedQuery
    EbayProduct

config/
    OpenAIConfig
    EbayConfig
```

The exact file structure may evolve during implementation, but changes should
have a clear architectural reason.

---

## 13. Development Milestones

### Milestone 1 — Foundation

Goal: establish the backend and database foundation.

Tasks:

- Create Spring Boot project
- Configure PostgreSQL
- Enable pgvector
- Create Product entity
- Create ProductRepository
- Add database migration
- Verify application can read/write product records
- Add basic tests for persistence

Do not integrate eBay or OpenAI yet.

---

### Milestone 2 — Product Ingestion

Goal: populate the local catalog with real product data.

Tasks:

- Integrate eBay Browse API
- Fetch fashion products
- Normalize external responses
- Map external products into Product entities
- Prevent duplicate imports
- Persist products
- Add ingestion tests

Do not implement semantic search yet.

---

### Milestone 3 — Product Embeddings

Goal: create searchable semantic representations of products.

Tasks:

- Integrate OpenAI embeddings API
- Build embedding text from product metadata
- Generate embeddings
- Store embeddings using pgvector
- Verify vector similarity queries
- Avoid regenerating unchanged embeddings unnecessarily

---

### Milestone 4 — Semantic Search

Goal: implement the core product search engine.

Tasks:

- Implement query understanding
- Extract structured constraints
- Generate query embedding
- Implement pgvector similarity search
- Combine vector search with price/category filtering
- Rank products
- Return top K results
- Add search tests

At the end of this milestone, the backend should be able to accept:

> "cream cropped cardigan, slightly oversized, under $100"

and return multiple ranked real products.

---

### Milestone 5 — React Frontend

Goal: provide a usable search experience.

Tasks:

- Create React application
- Build natural-language search box
- Add optional price/category filters
- Connect frontend to backend search API
- Display product cards
- Display multiple ranked results
- Link cards to original product pages
- Add loading state
- Add error state
- Add empty-results state

Keep the UI simple and functional.

---

### Milestone 6 — Testing and Deployment

Goal: make the project resume-ready.

Tasks:

- Expand backend unit tests
- Add integration tests for search
- Add validation
- Add centralized API error handling
- Configure environment variables
- Ensure API keys are never committed
- Deploy PostgreSQL + pgvector
- Deploy Spring Boot backend
- Deploy React frontend
- Test complete production flow
- Document architecture
- Document API
- Update README

---

## 14. Engineering Principles

1. External APIs provide data; they are not the search engine.

2. Search operates primarily against the application's local product catalog.

3. Product data is normalized before being stored.

4. Semantic similarity and structured SQL filtering work together.

5. The system returns multiple ranked choices rather than making a purchasing
   decision for the user.

6. OpenAI should not invent products. Every displayed product must correspond
   to a real record in the product catalog.

7. API keys and credentials must never be committed to Git.

8. Each milestone should remain independently testable.

9. Avoid unnecessary infrastructure until the MVP requires it.

10. Prefer a simple working end-to-end system over premature complexity.

11. Cursor/AI-generated code must be reviewable and understandable by the
    project owner.

12. Do not implement features outside the current milestone without explicit
    approval.