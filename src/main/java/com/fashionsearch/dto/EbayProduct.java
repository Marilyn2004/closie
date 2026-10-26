package com.fashionsearch.dto;

import java.math.BigDecimal;
import java.util.List;

public record EbayProduct(
    String itemId,
    String title,
    String shortDescription,
    String categoryName,
    BigDecimal price,
    String currency,
    String imageUrl,
    String itemWebUrl,
    String brand,
    String color,
    List<String> sizes,
    String availability) {
  public EbayProduct(
      String itemId,
      String title,
      String shortDescription,
      String categoryName,
      BigDecimal price,
      String currency,
      String imageUrl,
      String itemWebUrl) {
    this(
        itemId,
        title,
        shortDescription,
        categoryName,
        price,
        currency,
        imageUrl,
        itemWebUrl,
        null,
        null,
        null,
        "UNKNOWN");
  }
}
