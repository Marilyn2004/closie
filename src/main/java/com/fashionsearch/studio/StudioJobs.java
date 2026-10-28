package com.fashionsearch.studio;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class StudioJobs {
  final StudioStore store;
  final StudioService studio;
  final StudioAssets assets;
  final ImageGenerationProvider provider;
  final TransactionTemplate tx;
  final int limit;
  final ImageSpendLedger spend;

  public StudioJobs(
      StudioStore store,
      StudioService studio,
      StudioAssets assets,
      ImageGenerationProvider provider,
      PlatformTransactionManager manager,
      ImageSpendLedger spend,
      @Value("${closie.studio.daily-limit:12}") int limit) {
    this.spend = spend;
    this.store = store;
    this.studio = studio;
    this.assets = assets;
    this.provider = provider;
    this.tx = new TransactionTemplate(manager);
    this.limit = Math.max(0, Math.min(100, limit));
  }

  public record Job(
      UUID id,
      UUID revisionId,
      String operation,
      String state,
      int attempts,
      String error,
      JsonNode result) {}

  Job row(java.sql.ResultSet r) throws java.sql.SQLException {
    return new Job(
        r.getObject("id", UUID.class),
        r.getObject("revision_id", UUID.class),
        r.getString("operation"),
        r.getString("state"),
        r.getInt("attempts"),
        Objects.toString(r.getString("error"), ""),
        store.decode(Objects.toString(r.getString("result"), "{}")));
  }

  public Job get(String owner, UUID id) {
    return store
        .jdbc()
        .query("SELECT * FROM studio_jobs WHERE owner=? AND id=?", (r, n) -> row(r), owner, id)
        .stream()
        .findFirst()
        .orElseThrow(StudioStore::missing);
  }

  @Transactional
  public Job enqueue(String owner, UUID id, UUID rid, boolean doll) {
    return enqueue(owner, id, rid, doll, false);
  }

  @Transactional
  public Job enqueue(String owner, UUID id, UUID rid, boolean doll, boolean regenerate) {
    if (!provider.configured())
      throw new IllegalArgumentException(
          "Live image generation is off. Configure a backend image provider and an approved"
              + " request limit first. Your design brief is saved.");
    store.jdbc().execute("SELECT pg_advisory_xact_lock(63117002)");
    var d = store.get(owner, id);
    var revision = studio.selected(d, rid);
    String op = doll ? "DOLL" : "GARMENT";
    if (!d.kind().equals(doll ? "OUTFIT" : "CONCEPT"))
      throw new IllegalArgumentException("Choose the right item for this generation.");
    if (!doll) store.writable(owner, id);
    var existing =
        store
            .jdbc()
            .query(
                "SELECT * FROM studio_jobs WHERE owner=? AND revision_id=? AND operation=? AND"
                    + " state IN ('QUEUED','RUNNING','SUCCEEDED') ORDER BY created_at DESC LIMIT 1",
                (r, n) -> row(r),
                owner,
                revision.id(),
                op);
    if (!existing.isEmpty()) return existing.getFirst();
    int prior =
        store
            .jdbc()
            .queryForObject(
                "SELECT count(*) FROM studio_jobs WHERE owner=? AND revision_id=? AND operation=?",
                Integer.class,
                owner,
                revision.id(),
                op);
    if (prior >= 2)
      throw new IllegalArgumentException(
          "This image version has reached its two-attempt limit. Previous images are preserved.");
    Long all =
        store
            .jdbc()
            .queryForObject(
                "SELECT count(*) FROM studio_jobs WHERE created_at>now()-interval '24 hours'",
                Long.class);
    Long personal =
        store
            .jdbc()
            .queryForObject(
                "SELECT count(*) FROM studio_jobs WHERE owner=? AND created_at>now()-interval '24"
                    + " hours'",
                Long.class,
                owner);
    if (all >= limit || personal >= Math.min(6, limit))
      throw new IllegalArgumentException(
          "Daily image-generation limit reached. Saved work is still available.");
    String prompt, reference = "";
    if (doll) {
      prompt =
          "Original stylized 3D fashion doll, clearly an illustration rather than a person, no"
              + " brand imitation. White background, full body, natural wearable campus"
              + " proportions. Approximate these confirmed outfit descriptors, do not add text or"
              + " logos: "
              + revision.data().path("items").toString()
              + ". Preserve known anchor details where possible; this is styling inspiration, not"
              + " an exact product reproduction.";
      for (var item : revision.data().path("items"))
        if (item.path("id").asText().equals(d.data().path("anchorId").asText()))
          reference = item.path("image").asText("");
    } else {
      prompt = StyleLibrary.prompt(studio.brief(revision));
      reference =
          revision.data().path("referenceImage").asText(revision.data().path("image").asText(""));
    }
    UUID job = UUID.randomUUID();
    store
        .jdbc()
        .update(
            "INSERT INTO studio_jobs(id,owner,revision_id,operation,state,input)"
                + " VALUES(?,?,?,?,'QUEUED',?::jsonb)",
            job,
            owner,
            revision.id(),
            op,
            store.encode(
                Map.of(
                    "prompt",
                    prompt,
                    "reference",
                    reference,
                    "provider",
                    Objects.toString(provider.name(), "GEMINI"),
                    "settings",
                    Objects.toString(provider.settingsKey(), provider.model()),
                    "regenerate",
                    regenerate)));
    return get(owner, job);
  }

  public Map<String, Object> budgetSummary() {
    return spend.summary();
  }

  public Job cancel(String owner, UUID id) {
    store
        .jdbc()
        .update(
            "UPDATE studio_jobs SET state='CANCELLED',updated_at=now() WHERE id=? AND owner=? AND"
                + " state IN ('QUEUED','RUNNING')",
            id,
            owner);
    return get(owner, id);
  }

  @Scheduled(fixedDelayString = "${closie.studio.worker-delay-ms:1500}")
  public void tick() {
    if (!provider.configured()) return;
    // A crashed or timed-out request is failed, never automatically replayed (it may already be
    // billed).
    store
        .jdbc()
        .update(
            "UPDATE studio_jobs SET state='FAILED',error='Interrupted generation. Previous versions"
                + " remain saved.',updated_at=now() WHERE state='RUNNING' AND"
                + " updated_at<now()-interval '3 minutes'");
    var claim =
        store
            .jdbc()
            .query(
                "UPDATE studio_jobs SET state='RUNNING',attempts=attempts+1,updated_at=now() WHERE"
                    + " id=(SELECT id FROM studio_jobs WHERE state='QUEUED' ORDER BY created_at FOR"
                    + " UPDATE SKIP LOCKED LIMIT 1) RETURNING *",
                (r, n) ->
                    Map.of(
                        "id",
                        r.getObject("id", UUID.class),
                        "owner",
                        r.getString("owner"),
                        "input",
                        store.decode(r.getString("input")),
                        "revision",
                        r.getObject("revision_id", UUID.class),
                        "operation",
                        r.getString("operation")));
    if (claim.isEmpty()) return;
    var work = claim.getFirst();
    String owner = (String) work.get("owner");
    UUID id = (UUID) work.get("id");
    try {
      var input = (JsonNode) work.get("input");
      String ref = input.path("reference").asText();
      byte[] reference = ref.isBlank() ? null : assets.reference(owner, ref);
      String selectedProvider = Objects.toString(provider.name(), "GEMINI");
      String settings = Objects.toString(provider.settingsKey(), provider.model());
      if (!input.path("provider").asText().equals(selectedProvider)
          || !input.path("settings").asText().equals(settings))
        throw new IllegalArgumentException(
            "Image provider configuration changed. Submit a new image version before retrying.");
      String prompt = input.path("prompt").asText();
      if (selectedProvider.equals("OPENAI")) OpenAiImageCost.generationEstimate(prompt);
      String fingerprint = fingerprint(owner, settings, prompt, reference);
      store.jdbc().update("UPDATE studio_jobs SET request_key=? WHERE id=?", fingerprint, id);
      if (!input.path("regenerate").asBoolean()) {
        var cached =
            store
                .jdbc()
                .query(
                    "SELECT result FROM studio_jobs WHERE owner=? AND request_key=? AND"
                        + " state='SUCCEEDED' AND id<>? ORDER BY updated_at DESC LIMIT 1",
                    (r, n) -> store.decode(r.getString(1)),
                    owner,
                    fingerprint,
                    id);
        if (!cached.isEmpty()) {
          var metadata =
              new LinkedHashMap<String, Object>(
                  store.mapper().convertValue(cached.getFirst(), Map.class));
          try {
            byte[] cachedBytes =
                assets.reference(owner, Objects.toString(metadata.get("image"), ""));
            metadata.put("cached", true);
            metadata.put("usage", Map.of());
            metadata.put("estimatedCostUsd", java.math.BigDecimal.ZERO);
            metadata.put("costStatus", "LOCAL_CACHE_NO_PROVIDER_CALL");
            apply(work, id, owner, metadata, cachedBytes);
            return;
          } catch (java.io.IOException
              | org.springframework.web.server.ResponseStatusException unavailable) {
          }
        }
      }
      if (selectedProvider.equals("OPENAI")) {
        spend.reserve(id, owner, provider.model());
        spend.dispatched(id);
      }
      var generated = provider.generateWithMetadata(prompt, reference);
      if (selectedProvider.equals("OPENAI")) spend.complete(id, generated.metadata());
      var metadata = new LinkedHashMap<String, Object>(generated.metadata());
      metadata.put("provider", selectedProvider);
      metadata.put("model", provider.model());
      metadata.put("cached", false);
      apply(work, id, owner, metadata, generated.bytes());
    } catch (Exception e) {
      spend.unknown(id);
      String message =
          e instanceof OpenAiImages.ImageApiException || e instanceof IllegalArgumentException
              ? e.getMessage()
              : "Generation unavailable or image validation failed. Saved versions are preserved."
                  + " Retry is explicit and limited; an interrupted request may already be"
                  + " billed.";
      store
          .jdbc()
          .update(
              "UPDATE studio_jobs SET state='FAILED',error=?,updated_at=now() WHERE id=? AND"
                  + " state='RUNNING'",
              message.substring(0, Math.min(240, message.length())),
              id);
    }
  }

  private void apply(
      Map<String, Object> work,
      UUID id,
      String owner,
      Map<String, Object> metadata,
      byte[] generated) {
    tx.executeWithoutResult(
        status -> {
          var current =
              store
                  .jdbc()
                  .query(
                      "SELECT state FROM studio_jobs WHERE id=? FOR UPDATE",
                      (r, n) -> r.getString(1),
                      id);
          if (current.isEmpty() || !current.getFirst().equals("RUNNING")) return;
          String image = Objects.toString(metadata.get("image"), "");
          if (generated != null)
            try {
              image = assets.save(owner, generated);
            } catch (Exception e) {
              throw new IllegalArgumentException("Image failed file validation.");
            }
          metadata.put("image", image);
          metadata.put(
              "quality", "File validation passed; semantic and aesthetic human review required");
          if (work.get("operation").equals("GARMENT")) {
            var revision = store.revision((UUID) work.get("revision"));
            var data = (tools.jackson.databind.node.ObjectNode) revision.data().deepCopy();
            data.put("image", image);
            data.put("state", "READY");
            data.put("provider", Objects.toString(metadata.get("provider"), ""));
            data.put("model", Objects.toString(metadata.get("model"), ""));
            data.put("quality", Objects.toString(metadata.get("quality"), ""));
            store
                .jdbc()
                .update(
                    "UPDATE studio_revisions SET payload=?::jsonb WHERE id=?",
                    store.encode(data),
                    revision.id());
          }
          store
              .jdbc()
              .update(
                  "UPDATE studio_jobs SET state='SUCCEEDED',result=?::jsonb,updated_at=now()"
                      + " WHERE id=?",
                  store.encode(metadata),
                  id);
        });
  }

  private static String fingerprint(String owner, String settings, String prompt, byte[] reference)
      throws Exception {
    var digest = java.security.MessageDigest.getInstance("SHA-256");
    for (String field : List.of(owner, settings, prompt)) {
      byte[] bytes = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
      digest.update(bytes);
    }
    if (reference != null) digest.update(reference);
    return java.util.HexFormat.of().formatHex(digest.digest());
  }
}
