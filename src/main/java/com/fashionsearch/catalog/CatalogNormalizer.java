package com.fashionsearch.catalog;

import com.fashionsearch.model.Product;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CatalogNormalizer {
  public static String slot(String title, String category) {
    if (category != null && !category.isBlank()) {
      String known = textSlot(category.toLowerCase(Locale.ROOT));
      if (known != null) return known;
    }
    return textSlot(
        (Objects.toString(title, "") + " " + Objects.toString(category, ""))
            .toLowerCase(Locale.ROOT));
  }

  private static String textSlot(String t) {
    if (t.matches(".*\\b(bags?|totes?|handbags?|crossbody)\\b.*")) return "BAG";
    if (t.matches(".*\\bdress\\s+(pants|trousers)\\b.*")) return "BOTTOM";
    if (t.matches(".*\\bdress\\s+shirts?\\b.*")) return "TOP";
    if (t.matches(".*\\b(dress|dresses|jumpsuit|romper)\\b.*")) return "ONE_PIECE";
    if (t.matches(".*\\b(sneakers?|shoes?|boots?|sandals?|loafers?|heels?|flats)\\b.*"))
      return "SHOES";
    if (t.matches(".*\\b(jeans|trousers?|pants|skirts?|shorts)\\b.*")) return "BOTTOM";
    if (t.matches(".*\\b(jackets?|coats?|blazers?|vests?)\\b.*")) return "OUTERWEAR";
    if (t.matches(".*\\b(cardigans?|sweaters?|shirts?|blouses?|tops?|tees?|knitwear)\\b.*"))
      return "TOP";
    if (t.matches(
        ".*\\b(necklaces?|earrings?|bracelets?|rings?|jewelry|jewellery|bags?|belts?|scarves)\\b.*"))
      return "ACCESSORY";
    return null;
  }

  public void enrich(Product target, SourceProduct item) {
    target.setBrand(clean(item.brand(), 255));
    target.setRetailer(clean(item.retailer(), 255));
    String supplied = clean(item.outfitSlot(), 30);
    target.setOutfitSlot(
        Set.of("TOP", "BOTTOM", "ONE_PIECE", "SHOES", "OUTERWEAR", "ACCESSORY", "BAG")
                .contains(supplied == null ? "" : supplied)
            ? supplied
            : slot(item.title(), item.category()));
    target.setColor(clean(item.color(), 80));
    target.setAvailableSizes(item.sizes() == null ? null : String.join(",", item.sizes()));
    target.setAvailability(
        Set.of("AVAILABLE", "UNAVAILABLE", "UNKNOWN")
                .contains(item.availability() == null ? "" : item.availability())
            ? item.availability()
            : "UNKNOWN");
    target.setStyleTags(clean(item.styleTags(), 2000));
    target.setOccasionTags(clean(item.occasionTags(), 2000));
  }

  private String clean(String value, int max) {
    return value == null || value.isBlank() || value.trim().length() > max ? null : value.trim();
  }
}
