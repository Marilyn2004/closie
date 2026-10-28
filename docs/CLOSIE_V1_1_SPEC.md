# Closie — AI Fashion Stylist MVP Specification

**Version:** 1.1 — Consolidated Style My Item
**Product:** Closie
**Tagline:** Your Personal AI Stylist
**Repository:** `/Users/marilyn/Documents/ChatGPT/时尚谷/closie-v2`

## 1. Product Vision

Closie is an AI-powered fashion discovery and styling application focused initially on California college fashion.

The product helps users translate ordinary language into beautiful, realistic, wearable clothing inspiration.

Closie is not a generic image generator, a traditional fashion marketplace, or an outfit-only application.

**Product principle: The user brings the vibe. Closie figures out the rest.**

The existing product framework must remain:

**Discover → Find → Style**

- **Discover:** Personalized fashion inspiration and recommendations.
- **Find:** Natural-language clothing discovery and AI-generated fashion concepts.
- **Style:** Outfit recommendations for an AI-generated garment or an item the user already owns.

Users should never need professional prompt-engineering knowledge.

### Core User Journeys

**Journey A — Find My Style**

The user describes a desired aesthetic, occasion, garment, or mood in ordinary language. Closie interprets the request and presents attractive clothing concepts.

**Journey B — Style This**

The user selects a garment from Closie and asks for a coordinated outfit built around it.

**Journey C — Style My Item**

The user already owns a garment and wants outfit recommendations. They can provide a photo, brand/model information, or a product URL.

These are user journeys, not separate top-level navigation pages.

## 2. Existing Engineering Context

Closie V2 already has a foundation using:

- Java 21
- Spring Boot
- PostgreSQL
- JPA and Flyway
- React/Vite
- Discover, Find, and Style
- eBay product ingestion
- Optional LLM query interpretation
- Basic user preferences and favorites
- Backend and browser tests

Before implementing changes:

1. Inspect the latest repository state, branch, and working tree.
2. Read the original project specification and README.
3. Identify implemented functionality and existing tests.
4. Preserve working architecture and reusable components.
5. Apply non-destructive migrations where necessary.

Historical audit results must not be treated as current verification.

Do not rebuild the application from scratch.

## 3. Product and Data Strategy

Closie will prioritize AI-generated clothing inspiration rather than depend entirely on incomplete third-party retailer feeds.

Maintain distinct data types:

- `FashionConcept`: An AI-generated clothing concept without guaranteed retail availability.
- `RetailProduct`: A verified real-world product from an authorized data source.
- `OwnedItem`: A garment the user owns or identifies for styling.

These objects must not be presented interchangeably.

AI-generated concepts must not have fabricated brand names, retail prices, stock information, or purchasing links.

Keep the existing source-independent retail catalog architecture and eBay adapter for possible future shopping integration.

Do not delete existing data or integrations merely because they are no longer the primary discovery experience.

## 4. Target Aesthetic and Visual Identity

Initial audience: users interested in California college fashion.

Primary aesthetic family:

**California Campus / Playful Feminine Casual**

Related styles include:

- Everyday Campus
- Playful Y2K
- Pastel Cozy
- Girly Preppy
- Coastal Casual

Use previously provided fashion references as aesthetic guidance when available in the development context.

The design should feel youthful, playful, wearable, expressive, and visually cohesive.

Avoid generic luxury-editorial styling, unrelated clothing combinations, exaggerated fantasy garments, and costume-like designs.

### Clothing Image Standard

Default generated garment images must have:

- Individual garment only
- No human or mannequin
- Clean white or near-white background
- Entire garment visible
- Realistic stitching, seams, fabric weight, and construction
- Believable silhouettes
- Consistent professional product-photography presentation
- No invented logos or text labels
- No unrelated props

The aesthetic must be guided by curated references, not just vague adjectives such as "beautiful" or "fashionable."

## 5. AI Styling Engine

This is Closie's central intelligence layer.

Do not send the user's raw sentence directly to an image model as the complete instruction.

Implement a structured generation pipeline.

### Stage A — Interpret User Intent

Understand casual language such as:

- "I want something cute for class."
- "Something like Brandy but more playful."
- "A baby blue hoodie that isn't too basic."
- "Something feminine but comfortable."
- "I want to dress more like the girls on my Pinterest."

Extract:

- Garment category, if stated
- Style and aesthetic
- Occasion
- Colors
- Silhouette
- Fit preferences
- Desired level of detail
- Explicit dislikes
- Relevant user preferences
- Conversation context

Distinguish user-provided constraints from assumptions.

When the input is broad, generate multiple reasonable design directions rather than pretending the user specified a particular garment.

Ask clarifying questions only when essential.

### Stage B — Build a Fashion Design Brief

Produce a structured, validated brief for each candidate garment.

Include relevant attributes:

- Category
- Aesthetic
- Colors
- Silhouette
- Fit
- Fabric
- Neckline
- Sleeve shape
- Length
- Closure
- Design details
- Occasion
- Realism constraints
- Presentation requirements

Generate meaningful diversity across the candidate set.

### Stage C — Compose Image Generation Prompts

Convert the structured brief into a precise image-generation prompt.

Enforce consistent presentation, realism, and garment construction requirements.

Maintain a `StyleLibrary` of:

- Curated aesthetic descriptions
- Color palettes
- Silhouette vocabulary
- Fabric and construction guidance
- Appropriate outfit combinations
- Approved visual references
- High-quality examples

Only use visual references in ways permitted by their licensing and source terms.

### Stage D — Generate Images

Introduce a provider abstraction such as:

`ImageGenerationProvider`

Use Gemini as the preferred initial image provider when available and verified.

Allow alternative providers, such as OpenAI, without requiring a major business-logic rewrite.

Verify actual supported models and API request formats rather than hardcoding assumptions about model availability.

Keep API keys server-side.

### Stage E — Evaluate Results

Check generated images for:

- Prompt adherence
- User-intent alignment
- Realistic garment proportions
- Plausible construction
- Correct colors and silhouette
- Consistent background and presentation
- Obvious visual artifacts

Use bounded retries and user feedback.

Automated image-quality scores are not substitutes for human aesthetic evaluation.

## 6. Discover — Personalized Fashion Inspiration

Preserve the existing Discover feature.

Replace visually inconsistent, randomly ordered retail listings with curated fashion inspiration.

Support:

- AI-generated clothing concepts
- Curated style collections
- Category discovery
- User preferences
- Personalized ranking
- Saved concepts
- Loading and error states

Use existing curated concepts where possible.

Do not trigger expensive image generation every time the homepage loads.

Personalization can initially use explicit preferences, saves, and feedback-based ranking.

Users must be able to update or reset their preferences.

## 7. Find — Natural Language Clothing Discovery

**Find is the primary user-facing feature of Closie.**

### Main Workflow

1. User enters a natural-language fashion request.
2. Closie interprets the request using the Styling Engine.
3. Closie prepares several distinct garment design briefs.
4. Closie retrieves suitable saved concepts or generates new images.
5. Results appear progressively.
6. User can select, save, edit, regenerate, or style a result.

Generated clothing must be clearly labeled as fashion concepts, not guaranteed purchasable products.

### Conversational Refinement

Support:

- "Make it pink."
- "Less basic."
- "More feminine."
- "Something more fitted."
- "I prefer the second one."
- "Keep the same cardigan but change the neckline."

Retain the relevant conversational context and explicit constraints.

### Editing and Versioning

Each generated concept must have a persistent ID and revision history.

Support:

- Edit
- Regenerate
- Save
- Revert
- View previous versions

Store the associated design brief, image reference, generation metadata, and parent revision.

Use image-editing capabilities with the original image when possible.

Do not regenerate unrelated garment details when a user requests a localized modification.

Preserve visual identity as closely as possible, while acknowledging model limitations.

## 8. Style — AI Outfit Styling

Style supports two garment origins:

1. A FashionConcept selected from Closie.
2. An OwnedItem supplied by the user.

Both should flow into the same outfit-generation engine.

### Style This

A user selects an existing Closie fashion concept and requests a complete outfit.

The selected garment becomes the anchor item.

### Outfit Generation

Create coherent outfits using appropriate categories:

- Tops
- Bottoms
- Dresses
- Outerwear
- Shoes
- Bags
- Accessories

Not every outfit requires every category.

Consider:

- Color coordination
- Silhouette balance
- Occasion
- Wearability
- Season
- User preferences
- Anchor-item constraints

Preserve the anchor item unless the user explicitly requests its replacement.

### Outfit Board — Default View

Create a coordinated clothing composition using individual garment images.

Prefer independent item assets and deterministic layout rather than unnecessarily regenerating an entire collage.

Users should be able to select or replace individual pieces.

### Doll Preview — Optional View

Provide an optional AI-generated 3D fashion-doll visualization.

Use a consistent, original doll aesthetic.

Generate only when the user requests the preview.

Explain that the image illustrates styling inspiration and may not reproduce garment details perfectly.

### Outfit Refinement

Support:

- "Only change the shoes."
- "Try a skirt instead."
- "Keep the cardigan."
- "Make it more casual."
- "Add more pink."

Preserve unchanged items and maintain outfit revision history.

## 9. Style My Item — Unified Existing-Garment Styling

**Style My Item is one feature with multiple input methods. Do not split it into separate photo-styling and exact-product-search features.**

### User Goal

"I already own this garment. Help me figure out what to wear with it."

### Supported Input Methods

The user can identify their garment through any of the following:

**A. Upload a Photo**

Upload a photo of the garment.

Closie analyzes visible attributes such as:

- Clothing category
- Color
- Silhouette
- Fabric appearance
- Visible design details

Show the interpretation to the user for confirmation or correction.

**B. Brand and Model Search**

The user enters a brand, product name, model, SKU, and optionally a color.

Example:

"I have an Aritzia Super Puff Shorty in black."

Closie attempts to find a corresponding product using:

1. Existing verified retail catalog records.
2. Configured authorized retail search providers.
3. Permitted product-search integrations.

Rank results by brand, model, SKU, color, and identifiable attributes.

Use explicit match states:

- `EXACT_VERIFIED`
- `POSSIBLE_MATCH`
- `NOT_FOUND`

Never represent an uncertain or AI-inferred match as verified.

**C. Paste a Product URL**

The user pastes a retailer product link.

Retrieve product information only through permitted access methods.

Do not bypass website protections or assume all retailer pages are accessible.

### Shared Identification Workflow

All three inputs converge on the same sequence:

**Input → Identify Item → User Confirmation → OwnedItem → Outfit Generation**

Do not create independent styling engines or duplicate business logic for each input type.

### User Confirmation

Before styling, allow the user to confirm or correct:

- Garment type
- Color
- Fit
- Relevant details
- Matched product, if any

If there are multiple possible retail matches, display candidates.

If an exact match cannot be found, allow the user to upload a photo or provide a description.

Do not block styling merely because an external search provider lacks the item.

### OwnedItem Representation

Once confirmed, create an `OwnedItem` record containing the verified or user-confirmed garment attributes.

Preserve its origin:

- `USER_PHOTO`
- `BRAND_MODEL`
- `PRODUCT_URL`

Store the match confidence and source evidence separately from user-confirmed attributes.

Do not claim that an AI-generated approximation is the real retailer's exact garment.

### Outfit Generation

Pass the confirmed OwnedItem into the existing Style engine as an anchor garment.

Generate coordinated styling suggestions while preserving the item's actual known design.

If an image-generation process cannot faithfully preserve the original garment, display the user's source image separately and identify the generated result as an approximation.

### Privacy and Data Handling

Validate uploads and enforce reasonable file size and type limits.

Provide deletion controls for uploaded photos and associated saved records.

Do not unnecessarily transmit personal information or private photo metadata to AI providers.

Respect third-party product-image usage rights.

## 10. Data Model and Persistence

Extend the existing PostgreSQL schema using non-destructive Flyway migrations.

Suggested domain entities:

- `FashionConcept`
- `DesignBrief`
- `ConceptRevision`
- `GenerationJob`
- `StyleProfile`
- `SavedConcept`
- `Outfit`
- `OutfitItem`
- `OutfitRevision`
- `OwnedItem`
- `RetailProductMatch`
- `UserFeedback`

Use the minimum structure necessary for reliable product behavior.

Avoid redundant entities or overengineering.

Keep generated image files in an appropriate asset store with database references and metadata.

Preserve source and revision history.

Distinguish model-generated assumptions, verified external facts, and user-confirmed properties.

## 11. Performance and Cost Control

Implement:

- Asynchronous image-generation jobs
- Job status APIs
- Progressive image loading
- Request timeouts
- Bounded retries
- Reusable generated assets
- Caching and deduplication where appropriate
- Generation limits
- User-visible error handling
- Optional cancellation where supported
- Server-side usage safeguards

Do not expose image-model credentials to the frontend.

Do not regenerate unchanged garments during partial outfit edits.

Only generate Doll Previews when explicitly requested.

A failed generation must not erase previously saved work.

## 12. Testing and Verification

### Backend Tests

Verify:

- Intent extraction
- Structured design-brief validation
- Prompt composition
- Conversational context preservation
- Concept persistence and revisions
- Image-generation job state management
- Retry and caching behavior
- Outfit anchor preservation
- Individual outfit-item replacement
- OwnedItem identification
- Photo input handling
- Brand/model matching
- URL input handling
- User confirmation
- Exact/possible/not-found match status
- Shared styling flow across all OwnedItem inputs
- External API failure fallback

### Frontend Tests

Verify:

- Discover personalization
- Find natural-language workflow
- Progressive generation results
- Editing and reverting concepts
- Saving favorites
- Outfit Board
- Doll Preview
- Upload flow
- Brand/model search
- Product URL input
- Item confirmation
- OwnedItem styling
- Responsive mobile and desktop interfaces

### Real Model Evaluation

Evaluate at least 15–20 ordinary user requests.

Assess:

- Aesthetic quality
- Request adherence
- Realistic garment construction
- Consistency across edits
- Outfit coordination
- Correct preservation of anchor items

Clearly separate automated test results, mocked integrations, and verified live-model results.

Human review is required for aesthetic approval.

Do not claim verified live integration based only on mocked responses.

## 13. Security and Data Integrity

- Keep API credentials on the server.
- Validate all API requests.
- Protect expensive generation endpoints from abuse.
- Treat third-party content and user input as untrusted.
- Respect image and retailer data usage restrictions.
- Provide appropriate upload validation and deletion behavior.
- Avoid exposing private user data to unnecessary services.
- Clearly label generated clothing concepts.
- Never invent prices, brands, purchasing links, availability, or verified product matches.

## 14. Local Development and Documentation

Preserve the existing Java, Spring Boot, PostgreSQL, and React architecture.

Make the application reliable to start locally.

Update README with:

- Product description
- Implemented feature list
- Architecture overview
- Database design
- AI generation pipeline
- Environment configuration
- Provider setup
- Test instructions
- Example user journeys
- Screenshots
- Known limitations
- Future retail-product integration

The project should remain easy to explain during a Software Engineering interview.

Do not fabricate production usage, model quality results, business partnerships, or performance claims.

## 15. Definition of Done

The complete MVP must support the following end-to-end journeys:

### Discover

- Curated, visually consistent fashion inspiration.
- Basic personalized rankings.
- Saved preferences and favorites.

### Find

- Natural-language clothing discovery.
- Multiple meaningful garment concepts.
- Realistic white-background images.
- Editing, saving, and revisiting concepts.
- Conversational refinement.

### Style This

- Generate an outfit around a selected concept.
- Preserve the selected anchor garment.
- Display an Outfit Board.
- Support individual garment changes.
- Provide optional Doll Preview.

### Style My Item

- Accept a photo, brand/model description, or product URL.
- Use one unified identification workflow.
- Ask the user to confirm the garment.
- Attempt verified lookup where supported.
- Provide graceful fallback if an exact match is unavailable.
- Generate an outfit around the confirmed garment.
- Preserve its identified characteristics.

### Engineering

- Backend and frontend builds pass.
- Relevant automated tests pass.
- Real model integration is verified when credentials permit.
- Provider failures are handled.
- Local startup instructions work.
- User-facing claims accurately reflect actual capabilities.

## 16. Codex Execution Instructions

Complete the entire MVP as one integrated development assignment.

Do not stop after implementing one feature or ask for repeated milestone prompts.

You may independently make ordinary engineering decisions, implement features, refactor where necessary, add tests, fix defects, and update documentation.

Before making changes, inspect the latest repository and existing specification.

Work on a dedicated development branch.

Preserve existing application capabilities where practical.

Do not merge into main, deploy publicly, delete existing data, alter production resources, or perform destructive operations without explicit authorization.

If external credentials, paid API usage, permissions, or unavailable data block a feature, document the blocker and continue implementing independent functionality.

Do not fabricate success or substitute mock outputs for verified production behavior.

After completing the work, provide one consolidated engineering report:

1. Git branch and commits
2. Implemented user journeys
3. Frontend and backend changes
4. Database migrations
5. Image-generation provider status
6. OwnedItem identification and lookup status
7. Test and build results
8. Real-model evaluation results, if performed
9. Local startup instructions
10. Remaining blockers and limitations
11. Recruiter-demo readiness

**The objective is a complete, coherent, visually attractive AI Personal Stylist MVP — not merely a collection of individually implemented features.**
