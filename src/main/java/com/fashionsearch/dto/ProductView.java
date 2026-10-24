package com.fashionsearch.dto;

import com.fashionsearch.catalog.CatalogNormalizer;
import com.fashionsearch.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductView(
    Long id,
    String title,
    String description,
    String category,
    String brand,
    String retailer,
    String source,
    String outfitSlot,
    String color,
    String availableSizes,
    BigDecimal price,
    String currency,
    String imageUrl,
    String productUrl,
    String availability,
    Instant lastSeenAt,
    String styleTags,
    String occasionTags) {
  public static ProductView of(Product p) {
    return new ProductView(
        p.getId(),
        p.getTitle(),
        p.getDescription(),
        p.getCategory(),
        p.getBrand(),
        p.getRetailer() == null ? p.getSource() : p.getRetailer(),
        p.getSource(),
        p.getOutfitSlot() == null
            ? CatalogNormalizer.slot(p.getTitle(), p.getCategory())
            : p.getOutfitSlot(),
        p.getColor(),
        p.getAvailableSizes(),
        p.getPrice(),
        p.getCurrency(),
        p.getImageUrl(),
        p.getProductUrl(),
        p.getAvailability(),
        p.getLastSeenAt(),
        p.getStyleTags(),
        p.getOccasionTags());
  }
}
