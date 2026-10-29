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
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.studio.enabled=true",
      "closie.studio.asset-path=target/studio-test-assets",
      "closie.studio.worker-delay-ms=3600000",
      "closie.refresh.enabled=false",
      "closie.ai.enabled=false",
      "closie.studio.daily-limit=12",
      "closie.providers.permitted-sources=studio-test",
      "spring.servlet.multipart.max-file-size=8MB",
      "spring.servlet.multipart.max-request-size=9MB"
    })
@Testcontainers
class StudioJourneyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired StudioService studio;
  @Autowired StudioStore store;
  @Autowired StudioAssets assets;
  @Autowired StudioJobs jobs;
  @MockitoBean ImageGenerationProvider provider;
  @MockitoBean GeminiVision vision;
  @Autowired com.fashionsearch.repository.ProductRepository products;
  @org.springframework.boot.test.web.server.LocalServerPort int port;
  String owner;

  @BeforeEach
  void setup() {
    owner = UUID.randomUUID().toString().replace("-", "").repeat(2);
    when(provider.configured()).thenReturn(false);
    when(provider.model()).thenReturn("");
  }

  StudioStore.Document concept() {
    return studio.discover(owner, "").getFirst();
  }

  StudioStore.Document edited() {
    var result =
        studio.find(owner, new StudioService.FindRequest("make it lavender", concept().id(), null));
    return ((List<StudioStore.Document>) result.get("concepts")).getFirst();
  }

  byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  @Test
  void curatedGalleryIsOriginalAndHasNoCommerceFacts() {
    var all = studio.discover(owner, "");
    assertThat(all).hasSize(6);
    for (var d : all) {
      assertThat(d.kind()).isEqualTo("CONCEPT");
      assertThat(d.data().has("price")).isFalse();
      assertThat(studio.latest(d).data().path("provider").asText())
          .isEqualTo("BUILTIN_IMAGEGEN_CURATED");
    }
  }

  @Test
  void editCreatesOwnedCopyAndRevertPreservesBothVersions() {
    var seed = concept();
    var d = edited();
    assertThat(d.id()).isNotEqualTo(seed.id());
    assertThat(studio.latest(d).data().path("image").asText()).isEmpty();
    var next =
        (List<StudioStore.Document>)
            studio
                .find(owner, new StudioService.FindRequest("more fitted", d.id(), null))
                .get("concepts");
    assertThat(d.revisions()).hasSize(2);
    assertThat(d.revisions().getFirst().data().path("image").asText()).startsWith("/concepts/");
    assertThat(next.getFirst().revisions()).hasSize(3);
    var reverted = studio.revert(owner, d.id(), d.revisions().getLast().id());
    assertThat(reverted.revisions()).hasSize(4);
    assertThat(studio.brief(studio.latest(reverted)).color()).isEqualTo("lavender");
    assertThat(store.get(owner, seed.id()).revisions()).hasSize(1);
  }

  @Test
  void privateDocumentsCannotBeReadOrMutatedByOtherSessions() {
    var d = edited();
    assertThatThrownBy(() -> store.get("stranger", d.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> studio.revert("stranger", d.id(), d.revisions().getFirst().id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void preferencesRankButNeverGenerate() throws Exception {
    var seed = concept();
    studio.preferences(
        owner,
        new StudioService.Preferences(
            List.of(), List.of("blue"), List.of(seed.id()), List.of(), "college", "100"));
    assertThat(studio.preferences(owner).saved()).contains(seed.id());
    assertThat(studio.brief(studio.latest(studio.discover(owner, "").getFirst())).color())
        .contains("blue");
    verify(provider, never()).generate(anyString(), any());
  }

  @Test
  void anchorIsPreservedWhenOnlyBottomChanges() {
    var anchor = studio.discover(owner, "TOP").getFirst();
    var outfit = studio.outfit(owner, new StudioService.OutfitRequest(anchor.id(), null, "class"));
    var before = studio.latest(outfit).data().path("items");
    var bottom =
        studio.discover(owner, "BOTTOM").stream()
            .filter(d -> studio.brief(studio.latest(d)).garment().equals("skirt"))
            .findFirst()
            .orElseThrow();
    var next =
        studio.replace(owner, outfit.id(), new StudioService.ReplaceRequest("BOTTOM", bottom.id()));
    assertThat(next.revisions()).hasSize(2);
    var after = studio.latest(next).data().path("items");
    for (int i = 0; i < before.size(); i++)
      if (!before.get(i).path("slot").asText().equals("BOTTOM"))
        assertThat(after.get(i)).isEqualTo(before.get(i));
    assertThatThrownBy(
            () ->
                studio.replace(
                    owner,
                    outfit.id(),
                    new StudioService.ReplaceRequest(
                        "TOP", studio.discover(owner, "TOP").getLast().id())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allOwnedOriginsRequireConfirmationAndUseSharedAnchorEngine() throws Exception {
    for (String origin : List.of("USER_PHOTO", "BRAND_MODEL", "PRODUCT_URL")) {
      String photo = origin.equals("USER_PHOTO") ? assets.save(owner, png()) : "";
      var owned =
          studio.identify(
              owner,
              new StudioService.IdentifyRequest(
                  origin, "black puffer jacket", "https://example.com/unavailable-product", photo));
      assertThat(owned.data().path("matchStatus").asText()).isEqualTo("NOT_FOUND");
      assertThatThrownBy(
              () ->
                  studio.outfit(owner, new StudioService.OutfitRequest(owned.id(), null, "campus")))
          .isInstanceOf(IllegalArgumentException.class);
      studio.confirm(
          owner,
          owned.id(),
          new StudioService.ConfirmRequest("OUTERWEAR", "black", "relaxed", "puffer"));
      var outfit =
          studio.outfit(owner, new StudioService.OutfitRequest(owned.id(), null, "campus"));
      assertThat(studio.latest(outfit).data().path("items").get(0).path("id").asText())
          .isEqualTo(owned.id().toString());
      studio.delete(owner, owned.id());
      assertThatThrownBy(() -> store.get(owner, outfit.id()))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
  }

  @Test
  void uploadsValidateBytesDimensionsAndIsolation() throws Exception {
    assertThatThrownBy(() -> assets.save(owner, "fake jpeg".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> assets.save(owner, new byte[9 * 1024 * 1024]))
        .isInstanceOf(IllegalArgumentException.class);
    String photo = assets.save(owner, png());
    UUID id = UUID.fromString(photo.substring(photo.lastIndexOf('/') + 1));
    assertThat(assets.read(owner, id)[0]).isEqualTo((byte) 0xff);
    assertThatThrownBy(() -> assets.read("stranger", id))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assets.delete(owner, id);
    assertThatThrownBy(() -> assets.read(owner, id))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void unsafeUrlNeverFetched() {
    assertThatThrownBy(
            () ->
                studio.identify(
                    owner,
                    new StudioService.IdentifyRequest(
                        "PRODUCT_URL", "jacket", "file:///etc/passwd", "")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                studio.identify(
                    owner,
                    new StudioService.IdentifyRequest(
                        "PRODUCT_URL", "jacket", "http://127.0.0.1/", "")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void jobsAreOptInDeduplicatedAndPersistResults() throws Exception {
    var d = edited();
    var r = studio.latest(d);
    assertThatThrownBy(() -> jobs.enqueue(owner, d.id(), r.id(), false))
        .isInstanceOf(IllegalArgumentException.class);
    when(provider.configured()).thenReturn(true);
    when(provider.model()).thenReturn("test-only-mock");
    when(provider.generateWithMetadata(anyString(), any()))
        .thenReturn(new ImageGenerationProvider.GeneratedImage(png(), Map.of()));
    var job = jobs.enqueue(owner, d.id(), r.id(), false);
    assertThat(jobs.enqueue(owner, d.id(), r.id(), false).id()).isEqualTo(job.id());
    jobs.tick();
    assertThat(jobs.get(owner, job.id()).state()).isEqualTo("SUCCEEDED");
    assertThat(studio.latest(store.get(owner, d.id())).data().path("image").asText())
        .startsWith("/api/studio/assets/");
  }

  @Test
  void cancellationAndProviderFailureKeepOriginalRevision() throws Exception {
    when(provider.configured()).thenReturn(true);
    var d = edited();
    var r = studio.latest(d);
    var job = jobs.enqueue(owner, d.id(), r.id(), false);
    jobs.cancel(owner, job.id());
    jobs.tick();
    assertThat(jobs.get(owner, job.id()).state()).isEqualTo("CANCELLED");
    when(provider.generateWithMetadata(anyString(), any()))
        .thenThrow(new IOException("secret provider detail"));
    var retry = jobs.enqueue(owner, d.id(), r.id(), false);
    jobs.tick();
    assertThat(jobs.get(owner, retry.id()).state()).isEqualTo("FAILED");
    assertThat(jobs.get(owner, retry.id()).error()).doesNotContain("secret");
    assertThat(store.get(owner, d.id()).revisions()).hasSize(2);
  }

  @Test
  void browserJourneys() throws Exception {
    Assumptions.assumeTrue("true".equals(System.getenv("CLOSIE_BROWSER_SMOKE")));
    var builder =
        new ProcessBuilder(
                System.getenv().getOrDefault("CLOSIE_NODE", "node"),
                "node_modules/@playwright/test/cli.js",
                "test",
                "--config=playwright.studio.config.js")
            .directory(new java.io.File("frontend"))
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/studio-browser-smoke.log"));
    builder.environment().put("CLOSIE_API_URL", "http://127.0.0.1:" + port);
    builder.environment().put("CLOSIE_STUDIO_BROWSER", "true");
    var process = builder.start();
    boolean ended = process.waitFor(170, java.util.concurrent.TimeUnit.SECONDS);
    if (!ended) process.destroyForcibly();
    assertThat(ended).isTrue();
    assertThat(process.exitValue()).isZero();
  }

  @Test
  void verifiedUrlEvidenceIsSeparateFromPossibleNameMatch() {
    var product = new com.fashionsearch.model.Product();
    product.setExternalId(UUID.randomUUID().toString());
    product.setSource("studio-test");
    product.setTitle("Evidence Source Test Jacket");
    product.setBrand("Test");
    product.setPrice(new java.math.BigDecimal("12"));
    product.setCurrency("USD");
    product.setCategory("jacket");
    product.setOutfitSlot("OUTERWEAR");
    product.setAvailability("AVAILABLE");
    product.setImageUrl("https://example.com/authorized-fixture.jpg");
    product.setProductUrl("https://example.com/authorized-fixture");
    products.saveAndFlush(product);
    try {
      var exact =
          studio.identify(
              owner,
              new StudioService.IdentifyRequest(
                  "PRODUCT_URL", "my jacket", product.getProductUrl(), ""));
      assertThat(exact.data().path("matchStatus").asText()).isEqualTo("EXACT_VERIFIED");
      var possible =
          studio.identify(
              owner,
              new StudioService.IdentifyRequest(
                  "BRAND_MODEL", "Evidence Source Test Jacket", "", ""));
      assertThat(possible.data().path("matchStatus").asText()).isEqualTo("POSSIBLE_MATCH");
      assertThat(possible.data().path("confirmed").asBoolean()).isFalse();
    } finally {
      products.delete(product);
    }
  }

  @Test
  void photoAnalysisNeedsConsentAndStillNeedsConfirmation() throws Exception {
    String photo = assets.save(owner, png());
    when(vision.identify(anyString(), any()))
        .thenReturn(
            Optional.of(new GeminiVision.Attributes("TOP", "pink", "fitted", "ribbed cardigan")));
    var manual =
        studio.identify(
            owner, new StudioService.IdentifyRequest("USER_PHOTO", "cardigan", "", photo));
    assertThat(manual.data().has("visionAttributes")).isFalse();
    verify(vision, never()).identify(anyString(), any());
    var analyzed =
        studio.identify(
            owner, new StudioService.IdentifyRequest("USER_PHOTO", "cardigan", "", photo, true));
    assertThat(analyzed.data().path("visionAttributes").path("color").asText()).isEqualTo("pink");
    assertThat(analyzed.data().path("confirmed").asBoolean()).isFalse();
    assertThat(analyzed.data().path("matchStatus").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void generationBudgetLimitsRepeatedRequestsInOneBrowser() {
    when(provider.configured()).thenReturn(true);
    int queued = 0;
    try {
      for (int i = 0; i < 20; i++) {
        var result =
            studio.find(
                owner, new StudioService.FindRequest("lavender hoodie with " + i, null, null));
        var d = ((List<StudioStore.Document>) result.get("concepts")).getFirst();
        try {
          jobs.enqueue(owner, d.id(), studio.latest(d).id(), false);
          queued++;
        } catch (IllegalArgumentException expected) {
          break;
        }
      }
      assertThat(queued).isBetween(1, 6);
    } finally {
      store.jdbc().update("DELETE FROM studio_jobs WHERE owner=?", owner);
    }
  }
}
