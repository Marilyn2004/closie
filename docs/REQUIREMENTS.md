# Scope and verification contract

The current assignment is “Closie V2 — Build the Complete End-to-End MVP” supplied on 2026-10-26. Earlier Closie direction establishes Discover → Find → Style, real catalog grounding, personal aesthetics and budget awareness. These behaviors are retained.

`PROJECT_SPEC.md` is the original Fashion Search Engine V1 specification, preserved for context. Its old exclusions of favorites/conversation/personalization conflict with explicit V2 requirements; V2 authorizes these lightweight features. Its semantic-search roadmap is deferred because the current assignment explicitly permits keyword/filter retrieval and asks not to introduce embeddings unnecessarily. UI filters still take precedence, all product identities/prices come from the catalog, and model output cannot invent merchandise.

A fixture-based fallback is expressly allowed by this assignment. Therefore an original, clearly labeled sample collection now supports independent demos when external credentials are absent. It does not satisfy or claim live-commerce verification. Demo mode is isolated from the real catalog and has no purchase links. No third-party catalog-to-model permission is assumed; only original sample metadata is eligible for the outfit model. No material product requirement has been replaced with an invented merchant integration.

Definition of done here: reliable local launch; populated sample Discover; usable Find/refinement; conversational stylist, validated prices/budgets/currencies/identity; conditional model explanations with fallback; Saved/preferences; desktop/mobile; passing tests/build; accurate docs; explicit external verification blockers. Actual eBay/model calls remain contingent on authorized credentials.
