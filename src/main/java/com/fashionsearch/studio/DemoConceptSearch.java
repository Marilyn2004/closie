package com.fashionsearch.studio;

import java.util.*;
import java.util.regex.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Small, explainable catalog retrieval. It never creates a brief/image or calls a model. */
@Component
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class DemoConceptSearch {
  private static boolean word(String text, String term) {
    return Pattern.compile("(?<![a-z])" + Pattern.quote(term) + "(?![a-z])").matcher(text).find();
  }

  public List<StudioStore.Document> search(
      String query, List<StudioStore.Document> catalog, StyleLibrary.Brief previous) {
    String q = StyleLibrary.message(query).toLowerCase(Locale.ROOT);
    String garment = null, category = null;
    for (String term :
        List.of(
            "cardigan",
            "hoodie",
            "jeans",
            "skirt",
            "dress",
            "jacket",
            "sneakers",
            "shoes",
            "bag",
            "necklace"))
      if (word(q, term)) {
        garment = term;
        break;
      }
    if (garment == null && word(q, "top")) category = "TOP";
    if (garment == null && category == null && word(q, "bottoms")) category = "BOTTOM";
    if (previous != null && garment == null && category == null) garment = previous.garment();
    final String requiredGarment = garment, requiredCategory = category;
    var colors = new ArrayList<String>();
    var excluded = new ArrayList<String>();
    for (String c :
        List.of(
            "pink",
            "blue",
            "cream",
            "white",
            "black",
            "lavender",
            "green",
            "red",
            "brown",
            "yellow",
            "orange",
            "purple")) {
      if (q.matches(".*\\b(?:no|not|without|avoid)\\s+" + c + "\\b.*")) excluded.add(c);
      else if (word(q, c)) colors.add(c);
    }
    var positive =
        new HashSet<>(
            List.of(
                "cute",
                "class",
                "campus",
                "college",
                "feminine",
                "comfortable",
                "comfy",
                "cozy",
                "coastal",
                "casual",
                "outfit",
                "playful",
                "y2k",
                "pastel",
                "preppy",
                "everyday",
                "fitted",
                "relaxed",
                "soft",
                "knit",
                "zip",
                "denim",
                "cotton",
                "pearl",
                "style"));
    boolean recognized =
        garment != null
            || category != null
            || !colors.isEmpty()
            || !excluded.isEmpty()
            || positive.stream().anyMatch(t -> word(q, t));
    if (!recognized) return List.of();
    record Scored(StudioStore.Document doc, int score) {}
    var scored = new ArrayList<Scored>();
    for (var d : catalog) {
      var r = d.revisions().getLast();
      if (!r.data().path("state").asText().equals("READY")) continue;
      var b = r.data().path("brief");
      String actual = b.path("garment").asText();
      String color = b.path("color").asText();
      if (requiredGarment != null
          && !actual.equals(requiredGarment)
          && !(requiredGarment.equals("shoes") && b.path("category").asText().equals("SHOES")))
        continue;
      if (requiredCategory != null && !b.path("category").asText().equals(requiredCategory))
        continue;
      if (colors.stream().anyMatch(c -> !color.contains(c))
          || excluded.stream().anyMatch(color::contains)) continue;
      String searchable = (b.toString() + d.data().path("collections")).toLowerCase(Locale.ROOT);
      int score = 0;
      for (String token : q.split("[^a-z]+"))
        if (token.length() > 2 && searchable.contains(token)) score += 2;
      if (q.contains("no denim") && actual.equals("jeans")) continue;
      if (q.contains("more fitted") && !b.path("fit").asText().equals("fitted")) continue;
      scored.add(new Scored(d, score));
    }
    return scored.stream()
        .sorted(Comparator.comparingInt(Scored::score).reversed())
        .map(Scored::doc)
        .toList();
  }
}
