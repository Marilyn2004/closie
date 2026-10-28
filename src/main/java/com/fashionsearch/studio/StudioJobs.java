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

  public StudioJobs(
      StudioStore store,
      StudioService studio,
      StudioAssets assets,
      ImageGenerationProvider provider,
      PlatformTransactionManager manager,
      @Value("${closie.studio.daily-limit:12}") int limit) {
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
    if (!provider.configured())
      throw new IllegalArgumentException(
          "Live image generation is off. Configure the server-side Gemini provider and an approved"
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
            store.encode(Map.of("prompt", prompt, "reference", reference)));
    return get(owner, job);
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
      byte[] generated = provider.generate(input.path("prompt").asText(), reference);
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
            String image;
            try {
              image = assets.save(owner, generated);
            } catch (Exception e) {
              throw new IllegalArgumentException("Image failed file validation.");
            }
            var metadata =
                Map.of(
                    "image",
                    image,
                    "provider",
                    "GEMINI",
                    "model",
                    provider.model(),
                    "quality",
                    "File validation passed; semantic and aesthetic human review required");
            if (work.get("operation").equals("GARMENT")) {
              var revision = store.revision((UUID) work.get("revision"));
              var data = (tools.jackson.databind.node.ObjectNode) revision.data().deepCopy();
              data.put("image", image);
              data.put("state", "READY");
              data.put("provider", "GEMINI");
              data.put("model", provider.model());
              data.put("quality", metadata.get("quality"));
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
    } catch (Exception e) {
      store
          .jdbc()
          .update(
              "UPDATE studio_jobs SET state='FAILED',error='Generation unavailable or image"
                  + " validation failed. Saved versions are preserved. You may retry within the"
                  + " daily limit.',updated_at=now() WHERE id=? AND state='RUNNING'",
              id);
    }
  }
}
