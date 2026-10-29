package com.fashionsearch.service;

import static org.assertj.core.api.Assertions.*;

import com.fashionsearch.dto.*;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.providers.permitted-sources=fixture",
      "closie.providers.ebay.display-enabled=true",
      "closie.ai.enabled=false"
    })
@Testcontainers
class CatalogJourneyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired ProductRepository repository;
  @Autowired CatalogService catalog;
  @Autowired QueryInterpreter interpreter;
  @Autowired OutfitService outfits;
  @LocalServerPort int port;
  Product top, bottom, shoes, dress;

  @BeforeEach
  void seed() {
    repository.deleteAll();
    top =
        product(
            "Fixture ivory blouse",
            "TOP",
            "30.00",
            "USD",
            "ivory",
            "Studio Test",
            "feminine college");
    bottom =
        product(
            "Fixture blue trousers",
            "BOTTOM",
            "40.00",
            "USD",
            "blue",
            "Studio Test",
            "minimalist college");
    shoes =
        product(
            "Fixture white sneakers",
            "SHOES",
            "50.00",
            "USD",
            "white",
            "Studio Test",
            "casual college");
    dress =
        product(
            "Fixture black dress",
            "ONE_PIECE",
            "75.00",
            "USD",
            "black",
            "Other Test",
            "feminine dinner");
    product("Fixture expensive blouse", "TOP", "300.00", "USD", "ivory", "Studio Test", "feminine");
    product("Fixture euro blouse", "TOP", "10.00", "EUR", "ivory", "Studio Test", null);
  }

  Product product(
      String title,
      String slot,
      String price,
      String currency,
      String color,
      String brand,
      String tags) {
    Product p = new Product();
    p.setSource("fixture");
    p.setExternalId(UUID.randomUUID().toString());
    p.setTitle(title);
    p.setCategory(slot);
    p.setOutfitSlot(slot);
    p.setPrice(new BigDecimal(price));
    p.setCurrency(currency);
    p.setColor(color);
    p.setBrand(brand);
    p.setRetailer("Test retailer");
    p.setImageUrl("https://example.test/fixture.svg");
    p.setProductUrl("https://example.test/products/" + p.getExternalId());
    p.setAvailability("UNKNOWN");
    p.setLastSeenAt(Instant.now());
    p.setStyleTags(tags);
    return repository.saveAndFlush(p);
  }

  SearchContext context(
      String category, String brand, String color, String min, String max, String currency) {
    return new SearchContext(
        "",
        category,
        brand,
        color,
        min == null ? null : new BigDecimal(min),
        max == null ? null : new BigDecimal(max),
        currency,
        null,
        null);
  }

  @Test
  void enforcesAllStructuredFiltersInSql() {
    var result = catalog.search(context("TOP", "Studio Test", "ivory", "20", "60", "USD"), 0);
    assertThat(result.products()).extracting(ProductView::id).containsExactly(top.getId());
  }

  @Test
  void keywordAndCurrencyAreHardConstraints() {
    var c =
        new SearchContext(
            "blouse", null, null, null, null, new BigDecimal("100"), "USD", null, null);
    assertThat(catalog.search(c, 0).products())
        .extracting(ProductView::id)
        .containsExactly(top.getId());
  }

  @Test
  void wildcardInputDoesNotMatchEverything() {
    assertThat(
            catalog
                .search(new SearchContext("%", null, null, null, null, null, "USD", null, null), 0)
                .products())
        .isEmpty();
  }

  @Test
  void staleUnavailableAndUnreviewedProductsAreHidden() {
    top.setAvailability("UNAVAILABLE");
    repository.saveAndFlush(top);
    bottom.setSource("unreviewed");
    repository.saveAndFlush(bottom);
    shoes.setSource("ebay");
    shoes.setLastSeenAt(Instant.now().minus(Duration.ofHours(7)));
    repository.saveAndFlush(shoes);
    assertThat(catalog.search(SearchContext.empty(), 0).products())
        .extracting(ProductView::id)
        .doesNotContain(top.getId(), bottom.getId(), shoes.getId());
  }

  @Test
  void unconfiguredProviderIsHiddenEvenWhenFresh() {
    top.setSource("ebay");
    repository.saveAndFlush(top);
    var safeCatalog = new CatalogService(repository, false);
    safeCatalog.setPermittedSources("fixture");
    assertThat(safeCatalog.product(top.getId())).isEmpty();
  }

  @Test
  void productDetailsSupportNonDollarProducts() {
    var euro =
        repository.findAll().stream()
            .filter(p -> "EUR".equals(p.getCurrency()))
            .findFirst()
            .orElseThrow();
    assertThat(catalog.product(euro.getId()).orElseThrow().currency()).isEqualTo("EUR");
  }

  @Test
  void legacyEbayWithUnknownFreshnessIsHidden() {
    top.setSource("ebay");
    top.setLastSeenAt(null);
    repository.saveAndFlush(top);
    assertThat(catalog.product(top.getId())).isEmpty();
  }

  @Test
  void makesGroundedCompleteOutfitWithinBudget() {
    var outfit =
        outfits.style(
            new OutfitService.Request(
                "minimalist outfit for college under $150", null, null, top.getId()));
    assertThat(outfit.complete()).isTrue();
    assertThat(outfit.total()).isEqualByComparingTo("120");
    assertThat(outfit.products()).extracting(ProductView::id).doesNotHaveDuplicates();
    assertThat(outfit.products())
        .allMatch(p -> p.currency().equals("USD") && repository.existsById(p.id()));
  }

  @Test
  void impossibleBudgetIsHonestlyIncomplete() {
    var outfit = outfits.style(new OutfitService.Request("under $20", null, null, null));
    assertThat(outfit.complete()).isFalse();
    assertThat(outfit.missingSlots()).isNotEmpty();
    assertThat(outfit.total()).isLessThanOrEqualTo(new BigDecimal("20"));
  }

  @Test
  void dressRequestUsesOnePieceTemplate() {
    var outfit =
        outfits.style(new OutfitService.Request("dress for dinner under $150", null, null, null));
    assertThat(outfit.complete()).isTrue();
    assertThat(outfit.products())
        .extracting(ProductView::outfitSlot)
        .containsExactlyInAnyOrder("ONE_PIECE", "SHOES");
    assertThat(outfit.total()).isEqualByComparingTo("125");
  }

  @Test
  void rejectsOverBudgetAndCurrencyConflictingAnchors() {
    assertThatThrownBy(
            () -> outfits.style(new OutfitService.Request("under $10", null, null, top.getId())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> outfits.style(new OutfitService.Request("under €150", null, null, top.getId())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validatesHttpAndRefinesRealCatalogSearch() throws Exception {
    var client = HttpClient.newHttpClient();
    var json = JsonMapper.builder().build();
    var first =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/search"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"message\":\"minimalist outfit for college under $150\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(first.statusCode()).isEqualTo(200);
    var c = json.readTree(first.body()).get("context");
    var second =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/search"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"message\":\"Something more feminine\",\"context\":" + c + "}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(second.statusCode()).isEqualTo(200);
    var response = json.readTree(second.body());
    assertThat(response.path("context").path("maxPrice").asInt()).isEqualTo(150);
    assertThat(response.path("context").path("occasion").asString()).isEqualTo("college");
    assertThat(response.path("context").path("aesthetic").asString()).isEqualTo("feminine");
    var invalid =
        client.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/search?maxPrice=-1"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(invalid.statusCode()).isEqualTo(400);
  }

  @Test
  void canonicalCategoryNeverMatchesAnUnrelatedTitleWord() {
    product("Top handle handbag", "BAG", "20", "USD", "black", "Test", "casual");
    product("Low top sneakers", "SHOES", "20", "USD", "white", "Test", "casual");
    product("Soft bottom walking shoes", "SHOES", "20", "USD", "white", "Test", "casual");
    var tops =
        catalog.discover(
            new SearchContext("", "TOP", null, null, null, null, "USD", null, null),
            0,
            "relevance");
    assertThat(tops.products()).hasSize(2).allMatch(p -> "TOP".equals(p.outfitSlot()));
    assertThat(tops.products()).extracting(ProductView::id).contains(top.getId());
    var bottoms =
        catalog.discover(
            new SearchContext("", "BOTTOM", null, null, null, null, "USD", null, null),
            0,
            "relevance");
    assertThat(bottoms.products()).extracting(ProductView::id).containsExactly(bottom.getId());
  }

  @Test
  void browserJourneyAgainstRealBackend() throws Exception {
    Assumptions.assumeTrue(
        "true".equals(System.getenv("CLOSIE_BROWSER_SMOKE")),
        "Opt in with CLOSIE_BROWSER_SMOKE=true; uses isolated Testcontainers catalog, never live"
            + " listings.");
    var process =
        new ProcessBuilder(
                System.getenv().getOrDefault("CLOSIE_NODE", "node"),
                "node_modules/@playwright/test/cli.js",
                "test",
                "--grep-invert",
                "@demo")
            .directory(new java.io.File("frontend"))
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/browser-smoke.log"));
    process.environment().put("CLOSIE_API_URL", "http://127.0.0.1:" + port);
    var running = process.start();
    boolean finished = running.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
    if (!finished) running.destroyForcibly();
    assertThat(finished).isTrue();
    assertThat(running.exitValue()).isZero();
  }
}
