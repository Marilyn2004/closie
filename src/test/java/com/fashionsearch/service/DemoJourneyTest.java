package com.fashionsearch.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.catalog.DemoCatalog;
import com.fashionsearch.dto.*;
import com.fashionsearch.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.demo.enabled=true",
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/closie_demo",
      "closie.ai.enabled=false",
      "closie.refresh.enabled=false"
    })
@Testcontainers
class DemoJourneyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("pgvector/pgvector:pg17").withDatabaseName("closie_demo");

  @Autowired ProductRepository products;
  @Autowired CatalogService catalog;
  @Autowired OutfitService outfits;
  @Autowired QueryInterpreter interpreter;
  @Autowired DemoCatalog demo;
  @LocalServerPort int port;

  @Test
  void seededCollectionCoversAllSlotsAndIsIdempotent() {
    assertThat(products.count()).isEqualTo(29);
    demo.run(new DefaultApplicationArguments());
    assertThat(products.count()).isEqualTo(29);
    assertThat(products.findAll())
        .extracting(p -> p.getOutfitSlot())
        .contains("TOP", "BOTTOM", "ONE_PIECE", "SHOES", "OUTERWEAR", "BAG", "ACCESSORY");
    assertThat(catalog.search(SearchContext.empty(), 0).products()).allMatch(ProductView::demo);
  }

  @Test
  void realisticStylistPromptAndFollowupPreserveBudget() {
    var first =
        outfits.style(
            new OutfitService.Request(
                "I want an effortless feminine outfit for a coffee date. Something with an Aritzia"
                    + " vibe but a little more playful. My budget is $150.",
                null,
                null,
                null,
                true));
    assertThat(first.complete()).isTrue();
    assertThat(first.total()).isLessThanOrEqualTo(new BigDecimal("150"));
    assertThat(first.context().maxPrice()).isEqualByComparingTo("150");
    assertThat(first.context().vibe()).isEqualTo("aritzia");
    assertThat(first.context().brand()).isNull();
    assertThat(first.context().occasion()).isEqualTo("coffee date");
    assertThat(first.products()).extracting(ProductView::outfitSlot).contains("SHOES");
    var second =
        outfits.style(
            new OutfitService.Request(
                "Actually, make it suitable for a dinner date", first.context(), null, null, true));
    assertThat(second.context().occasion()).isEqualTo("dinner date");
    assertThat(second.context().maxPrice()).isEqualByComparingTo("150");
    assertThat(second.total()).isLessThanOrEqualTo(new BigDecimal("150"));
    assertThat(second.stylingMode()).isEqualTo("rules");
  }

  @Test
  void supportsEuroOutfitWithoutCurrencyMixing() {
    var result =
        outfits.style(
            new OutfitService.Request("feminine outfit under €100", null, null, null, true));
    assertThat(result.complete()).isTrue();
    assertThat(result.currency()).isEqualTo("EUR");
    assertThat(result.products()).allMatch(p -> p.currency().equals("EUR"));
    assertThat(result.total()).isEqualByComparingTo("95");
  }

  @Test
  void sortingIsAppliedToSqlResults() {
    var result = catalog.search(SearchContext.empty(), 0, "price_asc");
    assertThat(result.products()).extracting(ProductView::price).isSorted();
    assertThatThrownBy(() -> catalog.search(SearchContext.empty(), 0, "untrusted"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void savedRetrievalChecksCatalogAndReportsOnlyEligibleIds() {
    var id = products.findAll().getFirst().getId();
    var response = catalog.saved(List.of(id, Long.MAX_VALUE, id));
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().id()).isEqualTo(id);
    assertThatThrownBy(() -> catalog.saved(List.of(-1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidModelIdsDuplicateIdsAndOverBudgetPlansAreRejected() {
    var advisor = mock(AiOutfitAdvisor.class);
    var service = new OutfitService(catalog, interpreter, advisor);
    var candidates = catalog.candidates(SearchContext.empty());
    var id = candidates.getFirst().id();
    for (var ids : List.of(List.of(Long.MAX_VALUE), List.of(id, id))) {
      when(advisor.advise(any(), anyList(), anyList(), any()))
          .thenReturn(
              Optional.of(
                  new AiOutfitAdvisor.Proposal(
                      ids, "A malicious fabricated recommendation should not be accepted.")));
      var result = service.style(new OutfitService.Request("under $120", null, null, null, true));
      assertThat(result.stylingMode()).isEqualTo("rules");
      assertThat(result.total()).isLessThanOrEqualTo(new BigDecimal("120"));
      assertThat(result.products()).extracting(ProductView::id).doesNotHaveDuplicates();
    }
  }

  @Test
  void validModelPlanAndExplanationAreUsedAfterValidation() {
    var baseline = outfits.style(new OutfitService.Request("under $150", null, null, null, false));
    var advisor = mock(AiOutfitAdvisor.class);
    when(advisor.advise(any(), anyList(), anyList(), any()))
        .thenReturn(
            Optional.of(
                new AiOutfitAdvisor.Proposal(
                    baseline.products().stream().map(ProductView::id).toList(),
                    "The neutral palette keeps this look cohesive, with a simple silhouette and"
                        + " comfortable shoes.")));
    var result =
        new OutfitService(catalog, interpreter, advisor)
            .style(new OutfitService.Request("under $150", null, null, null, false));
    assertThat(result.stylingMode()).isEqualTo("ai-assisted");
    assertThat(result.explanation()).contains("neutral palette");
    assertThat(result.complete()).isTrue();
  }

  @Test
  void sampleBrowserJourney() throws Exception {
    Assumptions.assumeTrue("true".equals(System.getenv("CLOSIE_BROWSER_SMOKE")));
    var command =
        new ProcessBuilder(
                System.getenv().getOrDefault("CLOSIE_NODE", "node"),
                "node_modules/@playwright/test/cli.js",
                "test",
                "--grep",
                "@demo")
            .directory(new java.io.File("frontend"))
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/demo-browser-smoke.log"));
    command.environment().put("CLOSIE_API_URL", "http://127.0.0.1:" + port);
    var process = command.start();
    boolean ended = process.waitFor(110, java.util.concurrent.TimeUnit.SECONDS);
    if (!ended) process.destroyForcibly();
    assertThat(ended).isTrue();
    assertThat(process.exitValue()).isZero();
  }

  @Test
  void modelCannotExceedTotalBudgetEvenWhenEveryItemIndividuallyFits() {
    var advisor = mock(AiOutfitAdvisor.class);
    var all = catalog.candidates(SearchContext.empty());
    var ids =
        List.of("The tailored waistcoat", "The fluid tailored trouser", "The penny loafer").stream()
            .map(
                title ->
                    all.stream()
                        .filter(p -> p.title().equals(title))
                        .findFirst()
                        .orElseThrow()
                        .id())
            .toList();
    when(advisor.advise(any(), anyList(), anyList(), any()))
        .thenReturn(
            Optional.of(
                new AiOutfitAdvisor.Proposal(
                    ids, "A coordinated look that wrongly exceeds the total requested budget.")));
    var result =
        new OutfitService(catalog, interpreter, advisor)
            .style(new OutfitService.Request("under $100", null, null, null, false));
    assertThat(result.stylingMode()).isEqualTo("rules");
    assertThat(result.total()).isLessThanOrEqualTo(new BigDecimal("100"));
  }
}
