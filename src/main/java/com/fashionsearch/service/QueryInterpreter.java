package com.fashionsearch.service;

import com.fashionsearch.dto.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

@Service
public class QueryInterpreter {
  public record Interpretation(SearchContext context, String mode) {}

  private final UserQueryAi ai;

  public QueryInterpreter(UserQueryAi ai) {
    this.ai = ai;
  }

  public Interpretation interpret(SearchRequest request) {
    String message = request.message() == null ? "" : request.message().trim();
    if (message.length() > 1000)
      throw new IllegalArgumentException("Please keep requests under 1,000 characters.");
    SearchContext previous = request.context() == null ? SearchContext.empty() : request.context();
    SearchContext parsed = deterministic(message, previous);
    Optional<SearchContext> model =
        message.isBlank() ? Optional.empty() : ai.interpret(message, previous);
    // Deterministic explicit constraints always win over model interpretation.
    SearchContext context = parsed;
    if (model.isPresent()) {
      try {
        var proposed = merge(merge(previous, model.get()), explicit(message, parsed));
        validate(proposed);
        context = proposed;
      } catch (IllegalArgumentException invalidModel) {
        model = Optional.empty();
      }
    }
    context = merge(context, request.filters());
    validate(context);
    return new Interpretation(context, model.isPresent() ? "ai-assisted" : "rules");
  }

  private SearchContext explicit(String message, SearchContext parsed) {
    // Existing hard constraints survive refinements unless the rules or explicit UI update them.
    return new SearchContext(
        null,
        parsed.category(),
        parsed.brand(),
        parsed.color(),
        parsed.minPrice(),
        parsed.maxPrice(),
        parsed.currency(),
        null,
        null);
  }

  public SearchContext deterministic(String input, SearchContext old) {
    String text = input.toLowerCase(Locale.ROOT);
    if (text.isBlank()) return old;
    String category =
        pick(
            text,
            List.of(
                "jacket",
                "cardigan",
                "dress",
                "sneakers",
                "shirt",
                "blouse",
                "jeans",
                "trousers",
                "skirt",
                "necklace",
                "earrings",
                "bracelet",
                "boots",
                "sandals",
                "bag",
                "top",
                "shoes"));
    String color =
        pick(
            text,
            List.of(
                "black", "white", "cream", "ivory", "beige", "brown", "blue", "pink", "red",
                "green", "silver", "gold"));
    String aesthetic =
        pick(
            text,
            List.of(
                "minimalist",
                "minimal",
                "feminine",
                "casual",
                "classic",
                "romantic",
                "streetwear",
                "edgy"));
    String occasion =
        pick(text, List.of("college", "work", "dinner", "wedding", "weekend", "party"));
    Matcher max =
        Pattern.compile(
                "(?:under|below|up to|budget(?: of)?|less"
                    + " than|max(?:imum)?)\\s*(?:usd|eur|gbp)?\\s*[$€£]?\\s*(\\d+(?:\\.\\d{1,2})?)")
            .matcher(text);
    Matcher min =
        Pattern.compile("(?:over|above|at least|min(?:imum)?)\\s*[$€£]?\\s*(\\d+(?:\\.\\d{1,2})?)")
            .matcher(text);
    BigDecimal maximum = max.find() ? new BigDecimal(max.group(1)) : old.maxPrice();
    BigDecimal minimum = min.find() ? new BigDecimal(min.group(1)) : old.minPrice();
    if (text.contains("cheaper") && maximum != null)
      maximum = maximum.multiply(new BigDecimal("0.8")).setScale(2, java.math.RoundingMode.DOWN);
    String brand = old.brand();
    Matcher bm =
        Pattern.compile("(?:brand|by|from)\\s+([a-z][a-z0-9-]*(?:\\s+[a-z][a-z0-9-]*)?)")
            .matcher(text);
    if (bm.find()) brand = bm.group(1).replaceAll("\\s+(under|below|in|for).*$", "");
    String keywords = old.keywords();
    if (category != null) keywords = keywordPhrase(text, brand);
    else if (aesthetic == null
        && occasion == null
        && color == null
        && brand == old.brand()
        && !max.reset().find()
        && !text.contains("cheaper")
        && !min.reset().find())
      keywords =
          text.replaceAll("\\b(i|want|a|an|the|please|find|me|something|more|outfit|for)\\b", " ")
              .trim()
              .replaceAll("\\s+", " ");
    String currency = currencyIn(text);
    return new SearchContext(
        keywords,
        category == null ? old.category() : category,
        brand,
        color == null ? old.color() : color,
        minimum,
        maximum,
        currency == null ? old.currency() : currency,
        aesthetic == null ? old.aesthetic() : aesthetic,
        occasion == null ? old.occasion() : occasion);
  }

  private String keywordPhrase(String text, String brand) {
    String phrase =
        text.replaceAll(
            "(?:under|below|up to|budget(?: of)?|less than|max(?:imum)?|over|above|at"
                + " least|min(?:imum)?)\\s*(?:usd|eur|gbp)?\\s*[$€£]?\\s*\\d+(?:\\.\\d{1,2})?",
            " ");
    if (brand != null)
      phrase =
          phrase.replaceAll(
              "\\b(?:from|by|brand)\\s+" + Pattern.quote(brand.toLowerCase(Locale.ROOT)), " ");
    phrase =
        phrase.replaceAll(
            "\\b(i|want|a|an|the|please|find|me|something|more|outfit|for|in|with|and|that|is|to|wear|some|minimalist|minimal|feminine|casual|classic|romantic|streetwear|edgy|college|work|dinner|wedding|weekend|party|black|white|cream|ivory|beige|brown|blue|pink|red|green|silver|gold|usd|eur|gbp)\\b",
            " ");
    return phrase.trim().replaceAll("\\s+", " ");
  }

  private String currencyIn(String s) {
    s = s.toUpperCase(Locale.ROOT);
    return s.contains("€") || s.matches(".*\\bEUR\\b.*")
        ? "EUR"
        : s.contains("£") || s.matches(".*\\bGBP\\b.*")
            ? "GBP"
            : s.contains("$") || s.matches(".*\\bUSD\\b.*") ? "USD" : null;
  }

  private String pick(String text, List<String> words) {
    return words.stream()
        .filter(w -> Pattern.compile("\\b" + w + "s?\\b").matcher(text).find())
        .findFirst()
        .orElse(null);
  }

  private <T> T value(T newer, T older) {
    return newer == null ? older : newer;
  }

  public SearchContext merge(SearchContext old, SearchContext change) {
    if (change == null) return old;
    return new SearchContext(
        value(change.keywords(), old.keywords()),
        value(change.category(), old.category()),
        value(change.brand(), old.brand()),
        value(change.color(), old.color()),
        value(change.minPrice(), old.minPrice()),
        value(change.maxPrice(), old.maxPrice()),
        value(change.currency(), old.currency()),
        value(change.aesthetic(), old.aesthetic()),
        value(change.occasion(), old.occasion()));
  }

  public void validate(SearchContext c) {
    for (String s :
        Arrays.asList(
            c.keywords(), c.category(), c.brand(), c.color(), c.aesthetic(), c.occasion()))
      if (s != null && s.length() > 300)
        throw new IllegalArgumentException("A search preference is too long.");
    if (c.currency() == null || !Set.of("USD", "EUR", "GBP").contains(c.currency()))
      throw new IllegalArgumentException("Choose USD, EUR, or GBP.");
    if (c.minPrice() != null && c.minPrice().signum() < 0
        || c.maxPrice() != null && c.maxPrice().signum() < 0)
      throw new IllegalArgumentException("Prices must be zero or greater.");
    if (c.minPrice() != null && c.maxPrice() != null && c.minPrice().compareTo(c.maxPrice()) > 0)
      throw new IllegalArgumentException("Minimum price exceeds maximum price.");
  }
}
