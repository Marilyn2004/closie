package com.fashionsearch.studio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "closie.studio.enabled=true",
      "closie.studio.image-provider=openai",
      "closie.studio.asset-path=target/openai-pipeline-assets",
      "closie.studio.worker-delay-ms=3600000",
      "closie.ai.enabled=false",
      "closie.refresh.enabled=false"
    })
@Testcontainers
class OpenAiImagePipelineTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired StudioStore store;
  @Autowired StudioService studio;
  @Autowired StudioJobs jobs;
  @Autowired ImageSpendLedger spend;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean ImageGenerationProvider provider;
  String owner;

  @BeforeEach
  void setup() throws Exception {
    // This database is a disposable Testcontainer, never a user's local or production database.
    store.jdbc().update("DELETE FROM studio_jobs");
    store.jdbc().update("DELETE FROM studio_image_spend");
    owner = UUID.randomUUID().toString().replace("-", "").repeat(2);
    when(provider.configured()).thenReturn(true);
    when(provider.name()).thenReturn("OPENAI");
    when(provider.model()).thenReturn(OpenAiImages.MODEL);
    when(provider.settingsKey()).thenReturn("OPENAI:fixture:low:1024x1024");
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out);
    when(provider.generateWithMetadata(anyString(), any()))
        .thenReturn(
            new ImageGenerationProvider.GeneratedImage(
                out.toByteArray(),
                Map.of(
                    "usage",
                    Map.of(
                        "input_tokens_details",
                        Map.of("text_tokens", 100, "image_tokens", 0),
                        "output_tokens",
                        196),
                    "estimatedCostUsd",
                    new java.math.BigDecimal("0.00638"),
                    "requestId",
                    "req-local-test")));
  }

  StudioStore.Document draft(String who) {
    return ((List<StudioStore.Document>)
            studio
                .find(who, new StudioService.FindRequest("lavender fitted cardigan", null, null))
                .get("concepts"))
        .getFirst();
  }

  StudioJobs.Job generate(String who, StudioStore.Document d) {
    var j = jobs.enqueue(who, d.id(), studio.latest(d).id(), false);
    jobs.tick();
    return jobs.get(who, j.id());
  }

  @Test
  void reservesBeforeCallAndPersistsProviderUsageAndImageProvenance() throws Exception {
    var d = draft(owner);
    var j = generate(owner, d);
    assertThat(j.state()).isEqualTo("SUCCEEDED");
    assertThat(j.result().path("provider").asText()).isEqualTo("OPENAI");
    assertThat(j.result().path("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0.00638");
    assertThat(
            store
                .jdbc()
                .queryForObject(
                    "SELECT status FROM studio_image_spend WHERE job_id=?", String.class, j.id()))
        .isEqualTo("COMPLETED");
    assertThat(studio.latest(store.get(owner, d.id())).data().path("provider").asText())
        .isEqualTo("OPENAI");
    assertThat(spend.summary().get("reservedUsd").toString()).isEqualTo("0.250000");
  }

  @Test
  void exactPrivateCacheSkipsTheApiAndCopiesAssetLifecycle() throws Exception {
    var first = draft(owner);
    var second = draft(owner);
    var a = generate(owner, first);
    var b = generate(owner, second);
    assertThat(b.state()).isEqualTo("SUCCEEDED");
    assertThat(b.result().path("cached").asBoolean()).isTrue();
    assertThat(b.result().path("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0");
    assertThat(b.result().path("image").asText()).isNotEqualTo(a.result().path("image").asText());
    verify(provider, times(1)).generateWithMetadata(anyString(), any());
    assertThat(spend.summary().get("paidRequests")).isEqualTo(1L);
    studio.delete(owner, first.id());
    assertThat(store.get(owner, second.id()).revisions()).hasSize(1);
    assertThat(spend.summary().get("paidRequests")).isEqualTo(1L);
  }

  @Test
  void cacheNeverCrossesPrivateSessionsAndRegenerateIsExplicit() throws Exception {
    var first = draft(owner);
    generate(owner, first);
    String another = "b".repeat(64);
    generate(another, draft(another));
    verify(provider, times(2)).generateWithMetadata(anyString(), any());
    var second =
        ((List<StudioStore.Document>)
                studio
                    .find(
                        owner,
                        new StudioService.FindRequest(
                            "Keep the same garment",
                            first.id(),
                            studio.latest(store.get(owner, first.id())).id()))
                    .get("concepts"))
            .getFirst();
    var j = jobs.enqueue(owner, second.id(), studio.latest(second).id(), false, true);
    jobs.tick();
    assertThat(jobs.get(owner, j.id()).result().path("cached").asBoolean()).isFalse();
    verify(provider, times(3)).generateWithMetadata(anyString(), any());
  }

  @Test
  void referenceEditAndDollReuseExistingStylingEngineAndProvider() throws Exception {
    var seed = studio.discover(owner, "TOP").getFirst();
    var edited =
        ((List<StudioStore.Document>)
                studio
                    .find(owner, new StudioService.FindRequest("make it lavender", seed.id(), null))
                    .get("concepts"))
            .getFirst();
    generate(owner, edited);
    verify(provider)
        .generateWithMetadata(
            contains("Preserve all unchanged"),
            argThat(bytes -> bytes != null && bytes.length > 0));
    var outfit = studio.outfit(owner, new StudioService.OutfitRequest(seed.id(), null, "campus"));
    var j = jobs.enqueue(owner, outfit.id(), studio.latest(outfit).id(), true);
    jobs.tick();
    assertThat(jobs.get(owner, j.id()).state()).isEqualTo("SUCCEEDED");
    assertThat(jobs.get(owner, j.id()).operation()).isEqualTo("DOLL");
    verify(provider).generateWithMetadata(contains("Original stylized 3D fashion doll"), any());
    assertThat(studio.latest(outfit).data().path("items").get(0).path("id").asText())
        .isEqualTo(seed.id().toString());
  }

  @Test
  void cancelledJobDoesNotSpendAndFailureDoesNotAutomaticallyRetry() throws Exception {
    var d = draft(owner);
    var j = jobs.enqueue(owner, d.id(), studio.latest(d).id(), false);
    jobs.cancel(owner, j.id());
    jobs.tick();
    assertThat(spend.summary().get("paidRequests")).isEqualTo(0L);
    verify(provider, never()).generateWithMetadata(anyString(), any());
    when(provider.generateWithMetadata(anyString(), any()))
        .thenThrow(new java.net.http.HttpTimeoutException("private response details"));
    var retry = jobs.enqueue(owner, d.id(), studio.latest(d).id(), false);
    jobs.tick();
    jobs.tick();
    assertThat(jobs.get(owner, retry.id()).state()).isEqualTo("FAILED");
    assertThat(jobs.get(owner, retry.id()).error()).doesNotContain("private");
    assertThat(
            store
                .jdbc()
                .queryForObject(
                    "SELECT status FROM studio_image_spend WHERE job_id=?",
                    String.class,
                    retry.id()))
        .isEqualTo("UNKNOWN");
    verify(provider, times(1)).generateWithMetadata(anyString(), any());
    assertThatThrownBy(() -> jobs.enqueue(owner, d.id(), studio.latest(d).id(), false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oneRequestVerificationLimitIsDurableAndCannotBeResetByDeletingJobs() throws Exception {
    var single = new ImageSpendLedger(store, manager, 1);
    var id = UUID.randomUUID();
    single.reserve(id, owner, OpenAiImages.MODEL);
    assertThatThrownBy(
            () ->
                new ImageSpendLedger(store, manager, 1)
                    .reserve(UUID.randomUUID(), "different", OpenAiImages.MODEL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> single.reserve(id, owner, OpenAiImages.MODEL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(single.summary().get("paidRequests")).isEqualTo(1L);
  }

  @Test
  void concurrentReservationsCannotExceedThreeDollars() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var attempts = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 16; i++)
        attempts.add(
            pool.submit(
                () -> {
                  try {
                    spend.reserve(UUID.randomUUID(), owner, OpenAiImages.MODEL);
                    return true;
                  } catch (IllegalArgumentException expected) {
                    return false;
                  }
                }));
      int approved = 0;
      for (var attempt : attempts) if (attempt.get()) approved++;
      assertThat(approved).isEqualTo(12);
      assertThat(spend.summary().get("reservedUsd").toString()).isEqualTo("3.000000");
    } finally {
      pool.shutdownNow();
    }
  }
}
