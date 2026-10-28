# OpenAI clothing-image integration — 2026-10-28

## Result

**VERIFIED: one live OpenAI generation succeeded through the actual Closie backend.** The generated image is a realistic baby-blue fitted cardigan on a white background, with the full garment visible, no person and no visible logo. Agent visual inspection passed those presentation checks; user aesthetic acceptance is still separate.

- Model: `gpt-image-2.5-flare-2026-09-08`, a supported fixed snapshot.
- Endpoint: `POST https://api.openai.com/v1/images/generations`.
- Settings: one image, `quality=low`, `size=1024x1024`, `output_format=jpeg`, `background=opaque`.
- Reference inputs: none. No Responses/text-model call was added.
- Preflight: 952 UTF-8 prompt bytes; conservative estimate **$0.010640**, below $0.25.
- API usage: **192 text input tokens, 0 image input tokens, 196 image output tokens; 388 total**.
- Estimated usage cost: **$0.00684** = 192 × $5/1M + 196 × $30/1M.
- One provider request; no retries or fallback model requests.
- Request ID: `req_ac94612d521f44a9bec5664967022bde`.
- Job ID: `1a0f1901-a5cf-48c7-b972-24bb75fbda35`.

These are token-derived cost estimates, **not a verified billing statement or account balance**. The $9.86 prepaid balance was user-provided, not independently retrieved. No new OpenAI project was created; the existing project's backend key was used.

References checked: [Image guide](https://developers.openai.com/api/docs/guides/image-generation), [generation API](https://developers.openai.com/api/reference/resources/images/methods/generate), [edit API](https://developers.openai.com/api/reference/resources/images/methods/edit), [standard token rates](https://developers.openai.com/api/docs/pricing). The low/1024 calculator estimated 196 output tokens at $30/1M; actual output usage matched that token count. Pricing is a dated snapshot; recheck it before changing model, size or quality.

## Architecture and supported workflows

The existing `ImageGenerationProvider` remains the shared boundary. Its original `generate(prompt, reference)` method is preserved; default metadata methods keep the Gemini adapter compatible. `OpenAiImages` implements the same boundary and returns actual model/provider, request ID, reported usage and estimated cost.

- **Clothing generation:** validated StyleLibrary design brief → consistent garment prompt → JSON Images generation request → private asset and revision update.
- **Image editing:** the same structured pipeline plus the selected original image bytes → multipart `/images/edits`. The adapter passes original bytes, not a remote retailer URL; localized-preservation instructions remain in the brief prompt. Transport/pipeline tests passed; **no extra paid live edit was made**.
- **Outfits:** the existing shared outfit engine and deterministic individual-piece board remain unchanged. They can use OpenAI-generated concepts as anchors and do not regenerate unchanged pieces. Outfit-anchor and individual replacement browser tests pass.
- **Doll preview:** the existing explicit opt-in job uses the same provider, original anchor image when available and outfit descriptors, with approximation labeling. Pipeline-tested with a provider fixture; **no live paid doll request was made**.
- **Gemini:** retained as an operator-selectable provider. Existing Gemini HTTP tests pass; no new live Gemini calls were made.

No unrelated frontend design changes were made. The only UI change forwards explicit regeneration intent so a deliberate regeneration can bypass the reusable-result cache.

## Budget, caching and retries

`V8__image_spend_reservations.sql` adds owner-scoped request fingerprints and the permanent `studio_image_spend` ledger.

- Lifetime Closie OpenAI image allocation: **$3.00**.
- Conservative reservation: **$0.25 per dispatched paid request**; at most twelve reservations. Reservations remain even when actual estimated usage is lower, a job fails or a record is deleted. This is intentionally conservative and is not a claim that every call costs $0.25.
- Budget reservation is committed atomically before the provider call. Concurrent attempts cannot exceed the allocation. Deleting concepts/jobs does not reset it, and restarting preserves it.
- Current local verification mode has `maxPaidRequests=1`; after the successful call, subsequent paid jobs are blocked until the operator explicitly raises that setting. The post-restart read-only check confirmed one request, $0.25 held and $0.00684 token-derived estimate.
- Cache key includes owner, model/settings, structured prompt and reference-image bytes. Cached outputs do not cross users. Cache hits copy a private asset for independent deletion lifecycle and incur no API call. Explicit regeneration bypasses the cache; duplicate submissions for the same revision still return the same job.
- No automatic retries on timeout, auth, quota or API errors. A version allows at most two explicit job attempts, additionally constrained by the daily and dollar limits. Ambiguous failures retain their reservation because they may already be billed.
- Provider requests have an overall deadline, bounded response subscriber, one output and a 4000-byte prompt cap. There is no automatic model/quality escalation. Errors redact upstream response bodies; malformed keys are rejected without echoing them.
- Before the single live call, `/items/{id}/quote` computed a text-only estimate from the exact prompt and explicit output settings. Reference-image edits are not assigned the text-only estimate; their reported image input usage is accounted for after a response.

This local ledger does not control unrelated applications in the same OpenAI project, taxes, negotiated rates or future provider price changes. Other Closie text/vision paid paths are disabled by the verification launcher. Do not treat an application estimate as a provider-enforced invoice cap.

## Secret handling and startup

`OPENAI_API_KEY` is read only by backend configuration. It is never a Vite variable, request body, image metadata or committed value. The launcher removes OpenAI/Gemini provider keys from the frontend process environment. After the user's restart, a read-only process check confirmed both keys were absent from that frontend environment; no variable values were printed.

In a terminal, enter the key without recording it in shell history:

```zsh
cd "/Users/marilyn/Documents/ChatGPT/时尚谷/closie-v2"
read -s "OPENAI_API_KEY?Paste API key, then Enter (hidden): "
export OPENAI_API_KEY
echo
./dev openai
```

Use the existing API project. A newly created key in that same project is sufficient if the old secret is no longer visible; no new project is needed. Keep that terminal open. `./dev openai` disables text/vision paid calls and defaults to one paid-image reservation. Startup does not generate images.

To permit subsequent interactive use within the existing **$3** allocation, stop the local app and deliberately run in the same terminal:

```sh
CLOSIE_OPENAI_MAX_PAID_REQUESTS=12 ./dev openai
```

The completed verification remains counted. No additional paid requests were made during this assignment. The server stays local; no deployment or branch merge occurred.

Default `./dev studio` continues to leave generation disabled. To switch to Gemini, set `CLOSIE_IMAGE_PROVIDER=gemini`, enable generation and provide the existing Gemini server configuration separately. Never put keys in a frontend file or chat.

## Reproducible verification and tests

`scripts/verify_openai_image.py` talks only to the local backend. It never reads the OpenAI key. It requires the one-request cap and a zero-use starting ledger, checks the quote is <=$0.25 with no reference input, submits one image job and polls it. Recovery uses the recorded job ID rather than issuing a new paid call.

Evidence is private and ignored:

- `.local/openai-live-verification.json`: nonsecret model, usage, cost and job evidence.
- `.local/openai-live-verification.jpg`: downloaded output for local visual review.
- `.local/openai-live-verification-state.json`: private browser capability for recovering that job; not committed or printed.

Do not delete ledger rows to repeat the paid test. Do not mistake cached recovery for an additional live generation.

Automated verification:

- Final complete backend/browser regression: **141 JUnit items passed, zero failures/errors/skips**, including the added key-redaction/body-limit tests and backward compatibility for the optional regeneration request field.
- Browser suites: five studio journeys, three sample-catalog journeys and two retail journeys passed.
- Frontend production build, `bash -n dev`, script syntax and whitespace checks passed.
- New tests cover actual local HTTP request formatting, original-image multipart bytes, safe errors/no retries, body limits, malformed key redaction, usage estimates, budget concurrency/restart persistence, cache ownership/lifecycle, cancellation, explicit regeneration and shared outfit/doll provider flow.

No paid live editing or doll evaluation was performed, respecting the single-request authorization. Their visual faithfulness and broader styling quality remain unverified; the live result establishes successful single-clothing generation only.
