package com.fashionsearch.studio;

import com.fashionsearch.demo.PublicDemoPolicy;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
public class PublicDemoCatalog implements ApplicationRunner {
  final StudioStore store;
  final StudioService studio;
  final PublicDemoPolicy policy;

  public PublicDemoCatalog(StudioStore store, StudioService studio, PublicDemoPolicy policy) {
    this.store = store;
    this.studio = studio;
    this.policy = policy;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!policy.enabled()) return;
    store.jdbc().execute("SELECT pg_advisory_xact_lock(63117001)");
    var concepts = store.list("CURATED", "CONCEPT");
    if (concepts.stream()
        .noneMatch(
            d ->
                studio
                    .latest(d)
                    .data()
                    .path("image")
                    .asText()
                    .equals("/concepts/blue-cardigan.jpg"))) {
      var brief = StyleLibrary.interpret("baby blue fitted cardigan", null).getFirst();
      UUID id =
          store.create(
              "CURATED", "CONCEPT", Map.of("type", "FashionConcept", "title", brief.title()));
      store.append(
          id,
          null,
          Map.of(
              "brief",
              brief,
              "image",
              "/concepts/blue-cardigan.jpg",
              "state",
              "READY",
              "provider",
              "OPENAI_PREGENERATED_VERIFIED",
              "model",
              OpenAiImages.MODEL,
              "quality",
              "Pre-generated on 2026-10-28; no live generation in this demo"));
    }
    concepts = store.list("CURATED", "CONCEPT");
    for (var d : concepts) {
      var data = (tools.jackson.databind.node.ObjectNode) d.data().deepCopy();
      if (data.has("collections")) continue;
      String g = studio.brief(studio.latest(d)).garment();
      var collections =
          switch (g) {
            case "hoodie" -> List.of("Everyday Campus", "Pastel Cozy");
            case "jeans" -> List.of("Everyday Campus", "Coastal Casual", "Playful Y2K");
            case "skirt" -> List.of("Girly Preppy", "Playful Y2K");
            case "cardigan" -> List.of("Girly Preppy", "Pastel Cozy");
            default -> List.of("Everyday Campus", "Coastal Casual");
          };
      data.set("collections", store.mapper().valueToTree(collections));
      store
          .jdbc()
          .update(
              "UPDATE studio_documents SET payload=?::jsonb WHERE id=?",
              store.encode(data),
              d.id());
    }
    seedOutfit("campus-cozy", "The campus coffee run", "hoodie", "jeans");
    seedOutfit("girly-preppy", "A little pink for class", "cardigan", "skirt");
  }

  private void seedOutfit(String slug, String title, String top, String bottom) {
    if (store.list("CURATED", "OUTFIT").stream()
        .anyMatch(d -> d.data().path("slug").asText().equals(slug))) return;
    var all = store.list("CURATED", "CONCEPT");
    var anchor =
        all.stream()
            .filter(d -> studio.brief(studio.latest(d)).garment().equals(top))
            .filter(
                d ->
                    !studio.brief(studio.latest(d)).color().contains("blue")
                        || top.equals("hoodie"))
            .findFirst()
            .orElseThrow();
    var items = new ArrayList<Map<String, Object>>();
    items.add(studio.conceptItem(anchor, studio.latest(anchor)));
    for (String garment : List.of(bottom, "sneakers", "bag")) {
      var d =
          all.stream()
              .filter(c -> studio.brief(studio.latest(c)).garment().equals(garment))
              .findFirst()
              .orElseThrow();
      items.add(studio.conceptItem(d, studio.latest(d)));
    }
    UUID id =
        store.create(
            "CURATED",
            "OUTFIT",
            Map.of("type", "Outfit", "anchorId", anchor.id(), "slug", slug, "title", title));
    store.append(
        id,
        null,
        Map.of(
            "items",
            items,
            "anchorId",
            anchor.id(),
            "occasion",
            "class and campus",
            "missingSlots",
            List.of(),
            "explanation",
            "A pre-arranged campus outfit using original, pre-generated concepts. Save the look or"
                + " replace a complementary piece; no AI call is made."));
  }
}
