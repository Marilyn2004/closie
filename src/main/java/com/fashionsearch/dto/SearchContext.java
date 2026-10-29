package com.fashionsearch.dto;

import java.math.BigDecimal;

/** Validated user preferences; never authoritative product data. */
public record SearchContext(
    String keywords,
    String category,
    String brand,
    String color,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String currency,
    String aesthetic,
    String occasion,
    String vibe,
    String mood) {
  public SearchContext(
      String keywords,
      String category,
      String brand,
      String color,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String currency,
      String aesthetic,
      String occasion) {
    this(
        keywords, category, brand, color, minPrice, maxPrice, currency, aesthetic, occasion, null,
        null);
  }

  public static SearchContext empty() {
    return new SearchContext("", null, null, null, null, null, "USD", null, null);
  }
}
