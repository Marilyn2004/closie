package com.fashionsearch.service;

import com.fashionsearch.dto.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OutfitService {
  private final CatalogService catalog;
  private final QueryInterpreter interpreter;

  public OutfitService(CatalogService catalog, QueryInterpreter interpreter) {
    this.catalog = catalog;
    this.interpreter = interpreter;
  }

  public record Request(
      String message, SearchContext context, SearchContext filters, Long anchorId) {}

  public record Outfit(
      List<ProductView> products,
      BigDecimal total,
      String currency,
      boolean complete,
      List<String> missingSlots,
      String explanation,
      SearchContext context,
      String interpretationMode,
      boolean candidateLimitReached) {}

  public Outfit style(Request request) {
    var interpreted =
        interpreter.interpret(
            new SearchRequest(request.message(), request.context(), request.filters(), 0));
    SearchContext c = interpreted.context();
    // Search category/keywords describe an anchor, not every complementary slot.
    SearchContext complementary =
        new SearchContext(
            "",
            null,
            c.brand(),
            c.color(),
            c.minPrice(),
            c.maxPrice(),
            c.currency(),
            c.aesthetic(),
            c.occasion());
    List<ProductView> candidates = catalog.candidates(complementary);
    ProductView anchor =
        request.anchorId() == null
            ? null
            : catalog
                .product(request.anchorId())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Anchor is unavailable or not permitted for display."));
    if (anchor != null
        && (!anchor.currency().equals(c.currency())
            || c.maxPrice() != null && anchor.price().compareTo(c.maxPrice()) > 0))
      throw new IllegalArgumentException(
          "Anchor currency or price conflicts with the outfit budget.");
    if (anchor != null && anchor.outfitSlot() == null)
      throw new IllegalArgumentException("Anchor needs a known outfit slot.");
    if (anchor != null && !catalog.matches(anchor.id(), complementary))
      throw new IllegalArgumentException(
          "Anchor conflicts with the requested brand, color, or price filters.");
    String requestedSlot =
        com.fashionsearch.catalog.CatalogNormalizer.slot(c.keywords(), c.category());
    List<ProductView> best = List.of();
    int bestCoverage = -1;
    int bestScore = Integer.MIN_VALUE;
    List<String> bestMissing = List.of("TOP", "BOTTOM", "SHOES");
    for (List<String> template :
        List.of(List.of("TOP", "BOTTOM", "SHOES"), List.of("ONE_PIECE", "SHOES"))) {
      if ("ONE_PIECE".equals(requestedSlot) && !template.contains("ONE_PIECE")) continue;
      if (Set.of("TOP", "BOTTOM").contains(requestedSlot == null ? "" : requestedSlot)
          && !template.contains(requestedSlot)) continue;
      if (anchor != null
          && Set.of("TOP", "BOTTOM", "ONE_PIECE").contains(anchor.outfitSlot())
          && !template.contains(anchor.outfitSlot())) continue;
      List<ProductView> selected = new ArrayList<>();
      if (anchor != null) selected.add(anchor);
      List<String> missing = new ArrayList<>();
      for (String slot : template) {
        if (selected.stream().anyMatch(p -> slot.equals(p.outfitSlot()))) continue;
        // Cheapest compatible piece leaves maximum remaining budget; tie-break by preference
        // relevance.
        var next =
            candidates.stream()
                .filter(p -> slot.equals(p.outfitSlot()))
                .filter(p -> selected.stream().noneMatch(x -> x.id().equals(p.id())))
                .min(
                    Comparator.comparing(ProductView::price)
                        .thenComparing(
                            Comparator.comparingInt((ProductView p) -> score(p, c)).reversed()));
        if (next.isPresent()
            && (c.maxPrice() == null
                || total(selected).add(next.get().price()).compareTo(c.maxPrice()) <= 0))
          selected.add(next.get());
        else missing.add(slot);
      }
      int coverage = template.size() - missing.size();
      int merit = selected.stream().mapToInt(p -> score(p, c)).sum();
      boolean complete = missing.isEmpty(), bestComplete = bestMissing.isEmpty();
      if (complete && !bestComplete
          || complete == bestComplete
              && (coverage > bestCoverage || coverage == bestCoverage && merit > bestScore)) {
        best = List.copyOf(selected);
        bestMissing = List.copyOf(missing);
        bestCoverage = coverage;
        bestScore = merit;
      }
    }
    // Upgrade pieces toward stated aesthetics/occasion without breaking total budget or slot
    // coverage.
    var improved = new ArrayList<>(best);
    for (int i = 0; i < improved.size(); i++) {
      ProductView current = improved.get(i);
      if (anchor != null && anchor.id().equals(current.id())) continue;
      BigDecimal other = total(improved).subtract(current.price());
      var upgrade =
          candidates.stream()
              .filter(p -> Objects.equals(p.outfitSlot(), current.outfitSlot()))
              .filter(
                  p ->
                      improved.stream()
                          .noneMatch(x -> !x.id().equals(current.id()) && x.id().equals(p.id())))
              .filter(
                  p -> c.maxPrice() == null || other.add(p.price()).compareTo(c.maxPrice()) <= 0)
              .max(
                  Comparator.comparingInt((ProductView p) -> score(p, c))
                      .thenComparing(ProductView::price, Comparator.reverseOrder()));
      if (upgrade.isPresent()) improved.set(i, upgrade.get());
    }
    String explanation =
        bestMissing.isEmpty()
            ? "A complete outfit with complementary clothing and shoes, assembled from the catalog."
            : "This catalog cannot complete the outfit within your constraints. Missing: "
                + String.join(", ", bestMissing)
                + ".";
    explanation +=
        " Item subtotal only; shipping and tax are excluded. Availability is not checked live;"
            + " confirm with each retailer.";
    if (c.aesthetic() != null || c.occasion() != null)
      explanation +=
          " Preferences influence ordering only where catalog text or tags support them; matches"
              + " are not guaranteed.";
    if (candidates.size() >= 1000)
      explanation += " Selection considered the 1,000 lowest-priced compatible catalog items.";
    return new Outfit(
        List.copyOf(improved),
        total(improved),
        c.currency(),
        bestMissing.isEmpty(),
        bestMissing,
        explanation,
        c,
        interpreted.mode(),
        candidates.size() >= 1000);
  }

  private static BigDecimal total(List<ProductView> products) {
    return products.stream().map(ProductView::price).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private int score(ProductView p, SearchContext c) {
    String text =
        (p.title() + " " + p.description() + " " + p.styleTags() + " " + p.occasionTags())
            .toLowerCase(Locale.ROOT);
    int score = 0;
    for (String word : Arrays.asList(c.aesthetic(), c.occasion()))
      if (word != null && text.contains(word.toLowerCase(Locale.ROOT))) score += 10;
    return score;
  }
}
