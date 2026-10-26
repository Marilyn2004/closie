package com.fashionsearch.service;

import com.fashionsearch.dto.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OutfitService {
  private final CatalogService catalog;
  private final QueryInterpreter interpreter;
  private final AiOutfitAdvisor advisor;

  public OutfitService(CatalogService catalog, QueryInterpreter interpreter) {
    this(catalog, interpreter, null);
  }

  @Autowired
  public OutfitService(
      CatalogService catalog, QueryInterpreter interpreter, AiOutfitAdvisor advisor) {
    this.catalog = catalog;
    this.interpreter = interpreter;
    this.advisor = advisor;
  }

  public record Request(
      String message,
      SearchContext context,
      SearchContext filters,
      Long anchorId,
      boolean includeExtras) {
    public Request(String message, SearchContext context, SearchContext filters, Long anchorId) {
      this(message, context, filters, anchorId, false);
    }
  }

  public record Outfit(
      List<ProductView> products,
      BigDecimal total,
      String currency,
      boolean complete,
      List<String> missingSlots,
      String explanation,
      SearchContext context,
      String interpretationMode,
      boolean candidateLimitReached,
      String stylingMode) {}

  public Outfit style(Request request) {
    var parsed =
        interpreter.interpret(
            new SearchRequest(request.message(), request.context(), request.filters(), 0));
    var c = parsed.context();
    var constraints =
        new SearchContext(
            "",
            null,
            c.brand(),
            c.color(),
            c.minPrice(),
            c.maxPrice(),
            c.currency(),
            c.aesthetic(),
            c.occasion(),
            c.vibe(),
            c.mood());
    var candidates = catalog.candidates(constraints);
    var anchor =
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
    if (anchor != null && !catalog.matches(anchor.id(), constraints))
      throw new IllegalArgumentException("Anchor conflicts with requested filters.");
    String desired = com.fashionsearch.catalog.CatalogNormalizer.slot(c.keywords(), c.category());
    List<ProductView> best = List.of();
    List<String> missing = List.of("TOP", "BOTTOM", "SHOES");
    int merit = Integer.MIN_VALUE;
    boolean found = false;
    for (var template : List.of(List.of("TOP", "BOTTOM", "SHOES"), List.of("ONE_PIECE", "SHOES"))) {
      if ("ONE_PIECE".equals(desired) && !template.contains(desired)) continue;
      if (Set.of("TOP", "BOTTOM").contains(desired == null ? "" : desired)
          && !template.contains(desired)) continue;
      if (anchor != null
          && Set.of("TOP", "BOTTOM", "ONE_PIECE").contains(anchor.outfitSlot())
          && !template.contains(anchor.outfitSlot())) continue;
      // Enumerate a bounded set of candidates per slot. Include cheapest options as budget-safe
      // fallbacks.
      List<List<ProductView>> groups = new ArrayList<>();
      for (String slot : template) {
        if (anchor != null && slot.equals(anchor.outfitSlot())) {
          groups.add(List.of(anchor));
          continue;
        }
        var options = new LinkedHashMap<Long, ProductView>();
        candidates.stream()
            .filter(p -> slot.equals(p.outfitSlot()))
            .sorted(
                Comparator.comparingInt((ProductView p) -> score(p, c))
                    .reversed()
                    .thenComparing(ProductView::price))
            .limit(6)
            .forEach(p -> options.put(p.id(), p));
        candidates.stream()
            .filter(p -> slot.equals(p.outfitSlot()))
            .limit(2)
            .forEach(p -> options.put(p.id(), p));
        // null is a deliberate partial recommendation option.
        var choices = new ArrayList<>(options.values());
        choices.add(null);
        groups.add(choices);
      }
      var combinations = new ArrayList<List<ProductView>>();
      enumerate(groups, 0, new ArrayList<>(), combinations);
      for (var combo : combinations) {
        var selected = new ArrayList<>(combo);
        if (anchor != null && selected.stream().noneMatch(p -> p.id().equals(anchor.id())))
          selected.add(anchor);
        if (!within(selected, c)
            || selected.stream().map(ProductView::id).distinct().count() != selected.size())
          continue;
        var absent =
            template.stream()
                .filter(slot -> selected.stream().noneMatch(p -> slot.equals(p.outfitSlot())))
                .toList();
        int value =
            (absent.isEmpty() ? 100000 : 0)
                + (template.size() - absent.size()) * 1000
                + compatibility(selected, c);
        if (!found || value > merit) {
          found = true;
          merit = value;
          best = List.copyOf(selected);
          missing = absent;
        }
      }
    }
    var result = new ArrayList<>(best);
    if (request.includeExtras() && missing.isEmpty()) {
      var extras = new ArrayList<>(List.of("BAG", "ACCESSORY"));
      if ("OUTERWEAR".equals(desired)
          || Objects.toString(c.keywords(), "").matches(".*(jacket|coat|blazer).*"))
        extras.add(0, "OUTERWEAR");
      for (String slot : extras) {
        if (result.stream().anyMatch(p -> slot.equals(p.outfitSlot()))) continue;
        var next =
            candidates.stream()
                .filter(p -> slot.equals(p.outfitSlot()))
                .filter(
                    p ->
                        c.maxPrice() != null
                            && total(result).add(p.price()).compareTo(c.maxPrice()) <= 0)
                .max(
                    Comparator.comparingInt((ProductView p) -> score(p, c) + palette(result, p))
                        .thenComparing(ProductView::price, Comparator.reverseOrder()));
        next.ifPresent(result::add);
      }
    }
    String mode = "rules";
    String explanation = explain(result, c, missing);
    if (advisor != null) {
      var proposed = advisor.advise(c, candidates, result, request.anchorId());
      if (proposed.isPresent()) {
        var byId = new HashMap<Long, ProductView>();
        candidates.forEach(p -> byId.put(p.id(), p));
        if (anchor != null) byId.put(anchor.id(), anchor);
        var ids = proposed.get().ids();
        var suggested = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (valid(suggested, ids, c, anchor)
            && (missing.isEmpty() ? complete(suggested) : suggested.size() >= result.size())) {
          result.clear();
          result.addAll(suggested);
          missing = missing(result);
          explanation = proposed.get().explanation();
          mode = "ai-assisted";
        }
      }
    }
    // Eligibility can change while the model responds; re-read authoritative prices and display
    // policy.
    var refreshed =
        result.stream()
            .map(p -> catalog.product(p.id()))
            .flatMap(Optional::stream)
            .filter(p -> catalog.matches(p.id(), constraints))
            .toList();
    boolean changed = !refreshed.equals(result);
    result.clear();
    result.addAll(refreshed);
    if (anchor != null && result.stream().noneMatch(p -> p.id().equals(anchor.id())))
      throw new IllegalArgumentException(
          "The anchor changed or became unavailable. Please choose another piece.");
    while (!within(result, c) && !result.isEmpty()) {
      var removable =
          result.stream()
              .filter(p -> anchor == null || !p.id().equals(anchor.id()))
              .max(
                  Comparator.comparingInt(
                          (ProductView p) ->
                              Set.of("BAG", "ACCESSORY", "OUTERWEAR").contains(p.outfitSlot())
                                  ? 1
                                  : 0)
                      .thenComparing(ProductView::price));
      if (removable.isEmpty())
        throw new IllegalArgumentException("The anchor price changed and exceeds your budget.");
      result.remove(removable.get());
      changed = true;
    }
    if (changed) {
      missing = missing(result);
      explanation = explain(result, c, missing);
      mode = "rules";
    }
    explanation +=
        (result.stream().anyMatch(p -> p.demo())
                ? " Sample designs and prices; not for sale."
                : " Confirm sizes and stock with each retailer.")
            + " Item subtotal excludes shipping and tax.";
    return new Outfit(
        List.copyOf(result),
        total(result),
        c.currency(),
        missing.isEmpty(),
        missing,
        explanation,
        c,
        parsed.mode(),
        candidates.size() >= 1000,
        mode);
  }

  private boolean valid(
      List<ProductView> items, List<Long> ids, SearchContext c, ProductView anchor) {
    if (ids.size() != items.size()
        || ids.size() > 7
        || ids.stream().distinct().count() != ids.size()
        || !within(items, c)) return false;
    if (anchor != null && items.stream().noneMatch(p -> p.id().equals(anchor.id()))) return false;
    if (items.stream().map(ProductView::outfitSlot).distinct().count() != items.size())
      return false;
    if (items.stream().anyMatch(p -> "ONE_PIECE".equals(p.outfitSlot()))
        && items.stream().anyMatch(p -> Set.of("TOP", "BOTTOM").contains(p.outfitSlot())))
      return false;
    return true;
  }

  private void enumerate(
      List<List<ProductView>> groups,
      int index,
      List<ProductView> chosen,
      List<List<ProductView>> out) {
    if (index == groups.size()) {
      out.add(List.copyOf(chosen));
      return;
    }
    for (var p : groups.get(index)) {
      if (p != null) chosen.add(p);
      enumerate(groups, index + 1, chosen, out);
      if (p != null) chosen.remove(chosen.size() - 1);
    }
  }

  private boolean within(List<ProductView> items, SearchContext c) {
    return items.stream().allMatch(p -> c.currency().equals(p.currency()))
        && (c.maxPrice() == null || total(items).compareTo(c.maxPrice()) <= 0);
  }

  private boolean complete(List<ProductView> items) {
    return missing(items).isEmpty();
  }

  private List<String> missing(List<ProductView> items) {
    var slots = items.stream().map(ProductView::outfitSlot).toList();
    return (slots.contains("ONE_PIECE")
            ? List.of("ONE_PIECE", "SHOES")
            : List.of("TOP", "BOTTOM", "SHOES"))
        .stream().filter(s -> !slots.contains(s)).toList();
  }

  private int compatibility(List<ProductView> items, SearchContext c) {
    int score = items.stream().mapToInt(p -> score(p, c)).sum();
    for (var p : items) score += palette(items, p);
    return score;
  }

  private int palette(List<ProductView> items, ProductView p) {
    if (p.color() == null) return 0;
    int score = 0;
    for (var other : items) {
      if (other.id().equals(p.id()) || other.color() == null) continue;
      if (NEUTRALS.contains(other.color().toLowerCase(Locale.ROOT))
          || NEUTRALS.contains(p.color().toLowerCase(Locale.ROOT))) score += 2;
      else if (other.color().equalsIgnoreCase(p.color())) score += 2;
      else score -= 3;
    }
    return score;
  }

  private static final Set<String> NEUTRALS =
      Set.of("black", "white", "cream", "ivory", "beige", "brown", "gold", "silver");

  private int score(ProductView p, SearchContext c) {
    String text =
        (p.title() + " " + p.styleTags() + " " + p.occasionTags()).toLowerCase(Locale.ROOT);
    int result = 0;
    for (String term : Arrays.asList(c.aesthetic(), c.occasion(), c.vibe(), c.mood()))
      if (term != null && !term.isBlank() && text.contains(term.toLowerCase(Locale.ROOT)))
        result += 12;
    return result;
  }

  private String explain(List<ProductView> items, SearchContext c, List<String> missing) {
    if (items.isEmpty())
      return "There are no eligible pieces within these preferences. Try a broader palette, another"
                 + " currency, or a larger item budget.";
    String palette =
        items.stream()
            .map(ProductView::color)
            .filter(Objects::nonNull)
            .distinct()
            .limit(4)
            .collect(java.util.stream.Collectors.joining(", "));
    String result =
        "Start with "
            + items.get(0).title()
            + " and build around "
            + (palette.isEmpty() ? "its silhouette" : "the " + palette + " palette")
            + ". ";
    result +=
        missing.isEmpty()
            ? "The clothing and shoes form a complete base; optional finishing pieces stay inside"
                  + " your budget."
            : "This is a partial look: still missing "
                + String.join(", ", missing)
                + " within your constraints.";
    if (c.occasion() != null)
      result +=
          switch (c.occasion()) {
            case "work" ->
                " For work, keep the silhouette clean and the finishing details restrained.";
            case "college" ->
                " For college, an easy-to-wear base leaves room for a small personal detail.";
            case "dinner date", "dinner" ->
                " For a dinner date, let the shoes and finishing piece bring a more polished feel.";
            default ->
                " For "
                    + c.occasion()
                    + ", let one detail stand out while keeping the rest easy to wear.";
          };
    if (c.aesthetic() != null || c.mood() != null)
      result +=
          " The selection favors "
              + String.join(
                  " and ",
                  java.util.stream.Stream.of(c.aesthetic(), c.mood())
                      .filter(Objects::nonNull)
                      .toList())
              + " details where the catalog supports them.";
    return result;
  }

  private static BigDecimal total(List<ProductView> items) {
    return items.stream().map(ProductView::price).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
