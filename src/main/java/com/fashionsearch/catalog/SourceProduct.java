package com.fashionsearch.catalog;
import java.math.BigDecimal;
import java.util.List;
/** Provider-supplied facts; null means unknown, never inferred brand/stock. */
public record SourceProduct(String externalId, String title, String description, String category,
 BigDecimal price, String currency, String imageUrl, String productUrl, String brand,
 String retailer, String outfitSlot, String color, List<String> sizes, String availability,
 String styleTags, String occasionTags) {}
