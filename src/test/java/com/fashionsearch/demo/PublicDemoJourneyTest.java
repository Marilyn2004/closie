package com.fashionsearch.demo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.service.LlmGateway;
import com.fashionsearch.studio.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("public-demo")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.public-demo.enabled=false", // Profile membership must remain sufficient to block all
      // paid calls.
      "closie.studio.generation-enabled=true",
      "closie.studio.openai.key=fixture-private-key",
      "closie.studio.vision-enabled=true",
      "closie.studio.key=fixture-gemini-key",
      "closie.studio.vision-model=gemini-fixture",
      "closie.studio.text-daily-limit=100",
      "closie.ai.enabled=true",
      "closie.ai.key=fixture-private-key",
      "closie.ai.model=fixture",
      "closie.refresh.enabled=true",
      "closie.studio.worker-delay-ms=3600000"
    })
@Testcontainers
class PublicDemoJourneyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:pg17").withDatabaseName("closie_public_demo");

  @Autowired StudioService studio;
  @Autowired StudioStore store;
  @Autowired StudioJobs jobs;
  @Autowired ImageSpendLedger spend;
  @Autowired PublicDemoCatalog catalog;
  @Autowired PublicDemoPolicy policy;
  @MockitoSpyBean OpenAiImages images;
  @MockitoSpyBean GeminiVision vision;
  @MockitoSpyBean LlmGateway llm;
  @LocalServerPort int port;
  final JsonMapper json = JsonMapper.builder().build();
  final HttpClient http = HttpClient.newHttpClient();
  String token, owner;

  @BeforeEach
  void session() {
    token = UUID.randomUUID().toString().replace("-", "").repeat(2);
    owner = StudioService.owner(token);
  }

  HttpResponse<String> request(String method, String path, Object body) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Studio-Session", token)
            .header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void publicProfileWinsOverKeysAndUnsafeFeatureFlags() throws Exception {
    assertThat(policy.enabled()).isTrue();
    assertThat(images.configured()).isFalse();
    assertThat(vision.configured()).isFalse();
    assertThat(llm.configured()).isFalse();
    assertThatThrownBy(() -> images.generateWithMetadata("white top", null))
        .isInstanceOf(IllegalStateException.class);
    assertThat(llm.request("fixture", Map.of(), "instructions", Map.of())).isEmpty();
    assertThat(vision.identify(owner, new byte[] {1})).isEmpty();
    var status = json.readTree(request("GET", "/api/studio/status", null).body());
    assertThat(status.path("publicDemo").asBoolean()).isTrue();
    assertThat(status.path("imageConfigured").asBoolean()).isFalse();
    assertThat(
            json.readTree(request("GET", "/api/status", null).body())
                .path("aiConfigured")
                .asBoolean())
        .isFalse();
  }

  @Test
  void directPaidUploadAndAdministrativeRequestsAreForbidden() throws Exception {
    String id = studio.discover(owner, "").getFirst().id().toString();
    for (String path :
        List.of(
            "/api/studio/items/" + id + "/generate",
            "/api/studio/items/" + id + "/quote",
            "/api/studio/outfits/" + id + "/doll",
            "/api/studio/uploads",
            "/api/studio/owned/identify",
            "/api/admin/products/import",
            "/api/products/import",
            "/api/search",
            "/api/style",
            "/api/ebay/account-deletion"))
      assertThat(request("POST", path, Map.of("message", "hello")).statusCode())
          .as(path)
          .isEqualTo(403);
    for (String path :
        List.of("/api/studio/budget", "/api/admin/catalog/status", "/actuator/env", "/.env"))
      assertThat(request("GET", path, null).statusCode()).as(path).isEqualTo(403);
    jobs.tick();
    verify(images, never()).generateWithMetadata(anyString(), any());
    assertThat(store.jdbc().queryForObject("SELECT count(*) FROM studio_image_spend", Long.class))
        .isZero();
  }

  @Test
  void catalogIsIdempotentAndImagesAreBundled() throws Exception {
    var before = studio.discover(owner, "");
    assertThat(before).hasSize(7);
    var ids = before.stream().map(StudioStore.Document::id).toList();
    catalog.run(new org.springframework.boot.DefaultApplicationArguments());
    assertThat(studio.discover(owner, "").stream().map(StudioStore.Document::id))
        .containsExactlyElementsOf(ids);
    for (var d : before) {
      String path = d.revisions().getLast().data().path("image").asText();
      var response = request("GET", path, null);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }
  }

  @Test
  void findReturnsExistingRelevantConceptsAndNoNewRecords() throws Exception {
    long before = store.jdbc().queryForObject("SELECT count(*) FROM studio_documents", Long.class);
    var pink =
        json.readTree(
            request(
                    "POST",
                    "/api/studio/find",
                    Map.of("message", "a fitted pink cardigan for class"))
                .body());
    assertThat(pink.path("concepts").size()).isEqualTo(1);
    assertThat(pink.path("interpretationMode").asText()).isEqualTo("SAVED_CONCEPT_SEARCH");
    assertThat(
            json.readTree(
                    request("POST", "/api/studio/find", Map.of("message", "low-top shoes")).body())
                .path("concepts")
                .get(0)
                .path("revisions")
                .get(0)
                .path("data")
                .path("brief")
                .path("category")
                .asText())
        .isEqualTo("SHOES");
    assertThat(
            json.readTree(
                    request("POST", "/api/studio/find", Map.of("message", "green hoodie")).body())
                .path("concepts"))
        .isEmpty();
    assertThat(store.jdbc().queryForObject("SELECT count(*) FROM studio_documents", Long.class))
        .isEqualTo(before);
    verify(llm, never()).request(anyString(), anyMap(), anyString(), any());
  }

  @Test
  void prebuiltLookForksOnReplacementAndCanBeSaved() {
    var sample =
        store.list(owner, "OUTFIT").stream()
            .filter(d -> d.data().path("slug").asText().equals("campus-cozy"))
            .findFirst()
            .orElseThrow();
    var skirt =
        studio.discover(owner, "BOTTOM").stream()
            .filter(
                d ->
                    d.revisions()
                        .getLast()
                        .data()
                        .path("brief")
                        .path("garment")
                        .asText()
                        .equals("skirt"))
            .findFirst()
            .orElseThrow();
    var copy =
        studio.replace(owner, sample.id(), new StudioService.ReplaceRequest("BOTTOM", skirt.id()));
    assertThat(copy.id()).isNotEqualTo(sample.id());
    assertThat(copy.data().path("anchorId")).isEqualTo(sample.data().path("anchorId"));
    assertThat(store.get(owner, sample.id()).revisions()).hasSize(1);
    studio.preferences(
        owner,
        new StudioService.Preferences(List.of(), List.of(), List.of(copy.id()), List.of(), "", ""));
    assertThat(studio.preferences(owner).saved()).contains(copy.id());
    assertThatThrownBy(() -> store.get("another", copy.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void explicitPreferencesRankCuratedCollectionsAndCanReset() {
    studio.preferences(
        owner,
        new StudioService.Preferences(
            List.of("Coastal Casual"), List.of(), List.of(), List.of(), "", ""));
    var first = studio.discover(owner, "").getFirst();
    assertThat(first.data().path("collections").toString()).contains("Coastal Casual");
    assertThat(first.revisions().getLast().data().path("brief").path("category").asText())
        .isEqualTo("BOTTOM");
    studio.preferences(
        owner, new StudioService.Preferences(List.of(), List.of(), List.of(), List.of(), "", ""));
    assertThat(
            studio
                .discover(owner, "")
                .getFirst()
                .revisions()
                .getLast()
                .data()
                .path("brief")
                .path("category")
                .asText())
        .isEqualTo("TOP");
  }

  @Test
  void browserJourneys() throws Exception {
    Assumptions.assumeTrue("true".equals(System.getenv("CLOSIE_BROWSER_SMOKE")));
    var builder =
        new ProcessBuilder(
                System.getenv().getOrDefault("CLOSIE_NODE", "node"),
                "node_modules/@playwright/test/cli.js",
                "test",
                "--config=playwright.public.config.js")
            .directory(new java.io.File("frontend"))
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/public-demo-browser-smoke.log"));
    builder.environment().put("CLOSIE_API_URL", "http://127.0.0.1:" + port);
    builder.environment().put("CLOSIE_PUBLIC_BROWSER", "true");
    var process = builder.start();
    boolean ended = process.waitFor(150, java.util.concurrent.TimeUnit.SECONDS);
    if (!ended) process.destroyForcibly();
    assertThat(ended).isTrue();
    assertThat(process.exitValue()).isZero();
  }
}
