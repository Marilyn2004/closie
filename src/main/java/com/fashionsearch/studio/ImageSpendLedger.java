package com.fashionsearch.studio;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class ImageSpendLedger {
  private final StudioStore store;
  private final TransactionTemplate tx;
  private final int maxRequests;

  public ImageSpendLedger(
      StudioStore store,
      PlatformTransactionManager manager,
      @Value("${closie.studio.openai.max-paid-requests:12}") int maxRequests) {
    this.store = store;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.maxRequests = Math.max(0, Math.min(12, maxRequests));
  }

  public void reserve(UUID job, String owner, String model) {
    tx.executeWithoutResult(
        status -> {
          store.jdbc().execute("SELECT pg_advisory_xact_lock(63117008)");
          if (Boolean.TRUE.equals(
              store
                  .jdbc()
                  .queryForObject(
                      "SELECT EXISTS(SELECT 1 FROM studio_image_spend WHERE job_id=?)",
                      Boolean.class,
                      job)))
            throw new IllegalArgumentException(
                "This paid image request was already dispatched; it cannot be replayed"
                    + " automatically.");
          long count =
              store
                  .jdbc()
                  .queryForObject(
                      "SELECT count(*) FROM studio_image_spend WHERE provider='OPENAI'",
                      Long.class);
          var held =
              store
                  .jdbc()
                  .queryForObject(
                      "SELECT COALESCE(sum(reserved_usd),0) FROM studio_image_spend WHERE"
                          + " provider='OPENAI'",
                      java.math.BigDecimal.class);
          if (count >= maxRequests
              || held.add(OpenAiImageCost.RESERVATION).compareTo(OpenAiImageCost.PROJECT_LIMIT) > 0)
            throw new IllegalArgumentException(
                "Closie's OpenAI image budget or paid-request limit is reached. No provider request"
                    + " was sent.");
          store
              .jdbc()
              .update(
                  "INSERT INTO"
                      + " studio_image_spend(id,job_id,owner,provider,model,reserved_usd,status)"
                      + " VALUES(?,?,?,'OPENAI',?,?,'RESERVED')",
                  UUID.randomUUID(),
                  job,
                  owner,
                  model,
                  OpenAiImageCost.RESERVATION);
        });
  }

  public void dispatched(UUID job) {
    store.jdbc().update("UPDATE studio_image_spend SET status='DISPATCHED' WHERE job_id=?", job);
  }

  public void complete(UUID job, Map<String, Object> metadata) {
    store
        .jdbc()
        .update(
            "UPDATE studio_image_spend SET"
                + " status='COMPLETED',estimated_usd=?,usage=?::jsonb,request_id=? WHERE job_id=?",
            metadata.get("estimatedCostUsd"),
            store.encode(metadata.getOrDefault("usage", Map.of())),
            metadata.getOrDefault("requestId", ""),
            job);
  }

  public void unknown(UUID job) {
    store
        .jdbc()
        .update(
            "UPDATE studio_image_spend SET status='UNKNOWN' WHERE job_id=? AND status<>'COMPLETED'",
            job);
  }

  public Map<String, Object> summary() {
    return store
        .jdbc()
        .queryForObject(
            "SELECT count(*) AS requests,COALESCE(sum(reserved_usd),0) AS"
                + " held,COALESCE(sum(estimated_usd),0) AS estimated,count(*) FILTER(WHERE"
                + " estimated_usd IS NULL) AS unknown FROM studio_image_spend WHERE"
                + " provider='OPENAI'",
            (r, n) ->
                Map.of(
                    "provider",
                    "OPENAI",
                    "budgetUsd",
                    OpenAiImageCost.PROJECT_LIMIT,
                    "reservedUsd",
                    r.getBigDecimal("held"),
                    "reportedUsageEstimateUsd",
                    r.getBigDecimal("estimated"),
                    "unknownUsageRequests",
                    r.getLong("unknown"),
                    "paidRequests",
                    r.getLong("requests"),
                    "maxPaidRequests",
                    maxRequests));
  }
}
