package com.fashionsearch.studio;

import com.fashionsearch.dto.*;
import com.fashionsearch.service.CatalogService;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
@org.springframework.core.annotation.Order(0)
public class StudioService implements ApplicationRunner {
  final StudioStore store;
  final StudioAssets assets;
  final CatalogService catalog;
  final DesignBriefEngine engine;
  final GeminiVision vision;

  public StudioService(
      StudioStore store,
      StudioAssets assets,
      CatalogService catalog,
      DesignBriefEngine engine,
      GeminiVision vision) {
    this.store = store;
    this.assets = assets;
    this.catalog = catalog;
    this.engine = engine;
    this.vision = vision;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.fashionsearch.demo.PublicDemoPolicy demoPolicy;

  @org.springframework.beans.factory.annotation.Autowired private DemoConceptSearch demoSearch;

  public static String owner(String token) {
    if (token == null || !token.matches("[a-f0-9]{64}"))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED,
          "This browser needs a private studio session.");
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String[][] seeds = {
      {"pink-cardigan", "cardigan"},
      {"blue-hoodie", "hoodie"},
      {"denim-jeans", "jeans"},
      {"pink-skirt", "skirt"},
      {"white-sneakers", "sneakers"},
      {"cream-bag", "bag"}
    };
    store.jdbc().execute("SELECT pg_advisory_xact_lock(63117001)");
    if (!store.list("CURATED", "CONCEPT").isEmpty()) return;
    for (String[] seed : seeds) {
      var brief = StyleLibrary.defaults(seed[1], seed[1].equals("hoodie") ? 1 : 0);
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
              "/concepts/" + seed[0] + ".png",
              "state",
              "READY",
              "provider",
              "BUILTIN_IMAGEGEN_CURATED",
              "model",
              "tool-managed; not Gemini API",
              "quality",
              "Awaiting human aesthetic approval"));
    }
  }

  public record FindRequest(String message, UUID conceptId, UUID revisionId) {
    public FindRequest {
      message = StyleLibrary.message(message);
      if (revisionId != null && conceptId == null)
        throw new IllegalArgumentException("Choose a concept for that revision.");
    }
  }

  public record IdentifyRequest(
      String origin, String description, String url, String image, boolean analyzePhoto) {
    public IdentifyRequest {
      if (origin == null || !Set.of("USER_PHOTO", "BRAND_MODEL", "PRODUCT_URL").contains(origin))
        throw new IllegalArgumentException("Choose an input method.");
    }

    public IdentifyRequest(String origin, String description, String url, String image) {
      this(origin, description, url, image, false);
    }
  }

  public record ConfirmRequest(String category, String color, String fit, String details) {
    public ConfirmRequest {
      if (category == null || !StyleLibrary.CATEGORIES.contains(category))
        throw new IllegalArgumentException("Choose a garment category.");
      for (String value : Arrays.asList(color, fit, details))
        if (value == null || value.length() > 500)
          throw new IllegalArgumentException("Check your garment attributes.");
    }
  }

  public record OutfitRequest(UUID anchorId, UUID revisionId, String message) {
    public OutfitRequest {
      if (anchorId == null) throw new IllegalArgumentException("Choose an anchor garment.");
      if (message != null && message.length() > 1200)
        throw new IllegalArgumentException("Occasion description is too long.");
    }
  }

  public record ReplaceRequest(String slot, UUID conceptId) {
    public ReplaceRequest {
      if (slot == null || !StyleLibrary.CATEGORIES.contains(slot) || conceptId == null)
        throw new IllegalArgumentException("Choose a valid replacement piece.");
    }
  }

  public record Preferences(
      List<String> aesthetics,
      List<String> colors,
      List<UUID> saved,
      List<UUID> hidden,
      String lifestyle,
      String budget) {
    public Preferences {
      if (aesthetics == null
          || colors == null
          || saved == null
          || hidden == null
          || aesthetics.size() > 5
          || colors.size() > 12
          || saved.size() > 200
          || hidden.size() > 200) throw new IllegalArgumentException("Invalid preferences.");
      if (aesthetics.stream().anyMatch(Objects::isNull)
          || !StyleLibrary.STYLES.containsAll(aesthetics)
          || colors.stream().anyMatch(c -> c == null || c.length() > 40)
          || saved.stream().anyMatch(Objects::isNull)
          || hidden.stream().anyMatch(Objects::isNull)
          || lifestyle == null
          || budget == null
          || lifestyle.length() > 500
          || budget.length() > 40) throw new IllegalArgumentException("Invalid preferences.");
    }
  }

  public Preferences preferences(String owner) {
    return store.list(owner, "PROFILE").stream()
        .filter(d -> d.kind().equals("PROFILE"))
        .findFirst()
        .map(d -> store.convert(d.data(), Preferences.class))
        .orElse(new Preferences(List.of(), List.of(), List.of(), List.of(), "", ""));
  }

  @Transactional
  public Preferences preferences(String owner, Preferences value) {
    lockOwner(owner);
    for (UUID id : value.saved()) store.get(owner, id);
    store.jdbc().update("DELETE FROM studio_documents WHERE owner=? AND kind='PROFILE'", owner);
    store.create(owner, "PROFILE", value);
    return value;
  }

  public List<StudioStore.Document> discover(String owner, String category) {
    var p = preferences(owner);
    return store.list(owner, "CONCEPT").stream()
        .filter(d -> !p.hidden().contains(d.id()))
        .filter(
            d ->
                category == null
                    || category.isBlank()
                    || brief(latest(d)).category().equals(category))
        .sorted(
            Comparator.<StudioStore.Document>comparingInt(d -> score(d, p))
                .reversed()
                .thenComparingInt(
                    d ->
                        List.of(
                                "TOP",
                                "BOTTOM",
                                "ONE_PIECE",
                                "OUTERWEAR",
                                "SHOES",
                                "BAG",
                                "ACCESSORY")
                            .indexOf(brief(latest(d)).category()))
                .thenComparingInt(
                    d -> latest(d).data().path("state").asText().equals("READY") ? 0 : 1)
                .thenComparing(d -> brief(latest(d)).garment()))
        .toList();
  }

  private int score(StudioStore.Document d, Preferences p) {
    var b = brief(latest(d));
    boolean preferredStyle =
        p.aesthetics().contains(b.aesthetic())
            || java.util.stream.StreamSupport.stream(
                    d.data().path("collections").spliterator(), false)
                .anyMatch(node -> p.aesthetics().contains(node.asText()));
    return (preferredStyle ? 4 : 0)
        + (p.colors().stream().anyMatch(c -> b.color().contains(c.toLowerCase(Locale.ROOT)))
            ? 3
            : 0)
        + (p.saved().contains(d.id()) ? 2 : 0);
  }

  public StudioStore.Revision latest(StudioStore.Document d) {
    if (d.revisions().isEmpty()) throw StudioStore.missing();
    return d.revisions().getLast();
  }

  StyleLibrary.Brief brief(StudioStore.Revision r) {
    return store.convert(r.data().path("brief"), StyleLibrary.Brief.class);
  }

  public StudioStore.Revision selected(StudioStore.Document d, UUID rid) {
    return rid == null
        ? latest(d)
        : d.revisions().stream()
            .filter(r -> r.id().equals(rid))
            .findFirst()
            .orElseThrow(StudioStore::missing);
  }

  @Transactional
  public Map<String, Object> find(String owner, FindRequest request) {
    if (demoPolicy.enabled()) {
      StyleLibrary.Brief context = null;
      if (request.conceptId() != null) {
        var d = store.get(owner, request.conceptId());
        if (!d.kind().equals("CONCEPT")) throw new IllegalArgumentException("Select a concept.");
        context = brief(selected(d, request.revisionId()));
      }
      var found = demoSearch.search(request.message(), discover(owner, ""), context);
      return Map.of(
          "concepts",
          found,
          "interpretationMode",
          "SAVED_CONCEPT_SEARCH",
          "notice",
          found.isEmpty()
              ? "No existing concept matches that request. Try pink cardigan, blue hoodie, jeans or"
                  + " a broader color. This demo searches pre-generated concepts; it does not"
                  + " generate new images."
              : "Found in the pre-generated concept capsule. These are existing AI inspirations,"
                  + " not newly generated images or products for sale.");
    }
    lockOwner(owner);
    quotaDocuments(owner);
    StyleLibrary.Brief previous = null;
    StudioStore.Document original = null;
    StudioStore.Revision prior = null;
    if (request.conceptId() != null) {
      original = store.get(owner, request.conceptId());
      if (!original.kind().equals("CONCEPT"))
        throw new IllegalArgumentException("Select a concept to edit.");
      prior = selected(original, request.revisionId());
      if (original.revisions().size() >= 100)
        throw new IllegalArgumentException(
            "Version limit reached. Save a separate concept to continue.");
      previous = brief(prior);
    }
    var interpretation = engine.interpret(owner, request.message(), previous);
    var briefs = interpretation.briefs();
    var docs = new ArrayList<StudioStore.Document>();
    for (var b : briefs) {
      if (original == null) {
        // Exact structured-brief cache only. A blue picture never illustrates a newly requested
        // pink garment.
        var match =
            store.list(owner, "CONCEPT").stream()
                .filter(
                    d ->
                        sameDesign(brief(latest(d)), b)
                            && latest(d).data().path("state").asText().equals("READY"))
                .findFirst();
        if (match.isPresent()) {
          docs.add(match.get());
          continue;
        }
      }
      UUID id;
      UUID parent = null;
      if (original != null && isOwned(owner, original.id())) {
        id = original.id();
        parent = prior.id();
      } else {
        id = store.create(owner, "CONCEPT", Map.of("type", "FashionConcept", "title", b.title()));
        if (prior != null) parent = store.append(id, null, prior.data()).id();
      }
      var data = new LinkedHashMap<String, Object>();
      data.put("brief", b);
      data.put("state", "BRIEF_ONLY");
      data.put("image", "");
      data.put("provider", "STRUCTURED_RULES");
      data.put("instruction", request.message());
      if (prior != null) {
        data.put("sourceRevision", prior.id());
        data.put("referenceImage", prior.data().path("image").asText(""));
      }
      store.append(id, parent, data);
      docs.add(store.get(owner, id));
    }
    return Map.of(
        "concepts",
        docs,
        "interpretationMode",
        interpretation.mode(),
        "notice",
        "Design briefs use curated rules. Review inferred details. New images require the"
            + " configured provider; existing concept images remain available in Discover.");
  }

  private boolean sameDesign(StyleLibrary.Brief a, StyleLibrary.Brief b) {
    var x = (tools.jackson.databind.node.ObjectNode) store.mapper().valueToTree(a);
    var y = (tools.jackson.databind.node.ObjectNode) store.mapper().valueToTree(b);
    x.remove("assumptions");
    y.remove("assumptions");
    return x.equals(y);
  }

  private boolean isOwned(String owner, UUID id) {
    return Boolean.TRUE.equals(
        store
            .jdbc()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM studio_documents WHERE owner=? AND id=?)",
                Boolean.class,
                owner,
                id));
  }

  void lockOwner(String owner) {
    store
        .jdbc()
        .queryForObject(
            "SELECT pg_advisory_xact_lock(hashtextextended(?,63117))", Object.class, owner);
  }

  void quotaDocuments(String owner) {
    if (store
            .jdbc()
            .queryForObject(
                "SELECT count(*) FROM studio_documents WHERE owner=?", Long.class, owner)
        >= 300)
      throw new IllegalArgumentException(
          "Studio storage limit reached. Delete an item before continuing.");
  }

  @Transactional
  public StudioStore.Document revert(String owner, UUID id, UUID revision) {
    store.writable(owner, id);
    var d = store.get(owner, id);
    if (demoPolicy.enabled() && !d.kind().equals("OUTFIT")) demoPolicy.requirePrivate();
    var old = selected(d, revision);
    store.append(id, latest(d).id(), old.data());
    return store.get(owner, id);
  }

  @Transactional
  public StudioStore.Document identify(String owner, IdentifyRequest request) {
    demoPolicy.requirePrivate();
    lockOwner(owner);
    quotaDocuments(owner);
    if (!Set.of("USER_PHOTO", "BRAND_MODEL", "PRODUCT_URL").contains(request.origin()))
      throw new IllegalArgumentException("Choose photo, brand/model or URL.");
    String description =
        request.description() == null || request.description().isBlank()
            ? "my garment"
            : StyleLibrary.message(request.description());
    String url = request.url() == null ? "" : request.url();
    String image = request.image() == null ? "" : request.image();
    if (request.origin().equals("PRODUCT_URL")) {
      if (url.length() > 2048) throw new IllegalArgumentException("Link is too long.");
      try {
        var uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
          throw new Exception();
      } catch (Exception e) {
        throw new IllegalArgumentException("Paste a valid HTTPS product link.");
      }
    }
    if (!image.isBlank())
      try {
        assets.reference(owner, image);
      } catch (IOException e) {
        throw new IllegalArgumentException("Image unavailable.");
      }
    if (request.origin().equals("USER_PHOTO") && !image.startsWith("/api/studio/assets/"))
      throw new IllegalArgumentException("Upload your garment photo first.");
    var candidates = new ArrayList<Map<String, Object>>();
    for (var product : catalog.discover(SearchContext.empty(), 0, "relevance").products()) {
      if (product.demo()) continue;
      boolean urlMatch = !url.isBlank() && url.equals(product.productUrl());
      Set<String> terms =
          new HashSet<>(List.of(description.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
      String title = product.title().toLowerCase(Locale.ROOT);
      long overlap = terms.stream().filter(t -> t.length() > 2 && title.contains(t)).count();
      if (urlMatch || overlap >= 2)
        candidates.add(
            Map.of(
                "product",
                product,
                "status",
                urlMatch ? "EXACT_VERIFIED" : "POSSIBLE_MATCH",
                "evidence",
                urlMatch
                    ? "Exact URL in currently eligible authorized catalog"
                    : "Title overlap only; model/SKU not independently verified"));
      if (candidates.size() >= 6) break;
    }
    String match =
        candidates.stream().anyMatch(c -> c.get("status").equals("EXACT_VERIFIED"))
            ? "EXACT_VERIFIED"
            : candidates.isEmpty() ? "NOT_FOUND" : "POSSIBLE_MATCH";
    var suggestions = StyleLibrary.interpret(description, null).getFirst();
    var data = new LinkedHashMap<String, Object>();
    data.put("type", "OwnedItem");
    data.put("origin", request.origin());
    data.put("description", description);
    data.put("url", url);
    data.put("image", image);
    data.put("confirmed", false);
    data.put("matchStatus", match);
    data.put("candidates", candidates);
    data.put("suggested", suggestions);
    if (request.origin().equals("USER_PHOTO") && request.analyzePhoto()) {
      try {
        var analysis = vision.identify(owner, assets.reference(owner, image));
        if (analysis.isPresent()) data.put("visionAttributes", analysis.get());
      } catch (IOException ignored) {
      }
    }
    data.put(
        "identification",
        request.origin().equals("USER_PHOTO")
            ? (data.containsKey("visionAttributes")
                ? "AI_PHOTO_SUGGESTION: confirm or correct visible attributes; not verified product"
                    + " facts"
                : "MANUAL_CONFIRMATION_REQUIRED: vision disabled, unavailable or inconclusive")
            : "Bounded existing-catalog lookup; no arbitrary retailer scraping");
    UUID id = store.create(owner, "OWNED", data);
    return store.get(owner, id);
  }

  @Transactional
  public StudioStore.Document confirm(String owner, UUID id, ConfirmRequest r) {
    demoPolicy.requirePrivate();
    store.writable(owner, id);
    var d = store.get(owner, id);
    if (!d.kind().equals("OWNED")) throw new IllegalArgumentException("Select an owned item.");
    if (!StyleLibrary.CATEGORIES.contains(r.category()))
      throw new IllegalArgumentException("Choose a category.");
    for (String value : List.of(r.color(), r.fit(), r.details()))
      if (value == null || value.length() > 500)
        throw new IllegalArgumentException("Check item details.");
    var data = (tools.jackson.databind.node.ObjectNode) d.data().deepCopy();
    data.put("confirmed", true);
    data.set("attributes", store.mapper().valueToTree(r));
    store
        .jdbc()
        .update(
            "UPDATE studio_documents SET payload=?::jsonb WHERE id=? AND owner=?",
            store.encode(data),
            id,
            owner);
    return store.get(owner, id);
  }

  Map<String, Object> conceptItem(StudioStore.Document d, StudioStore.Revision r) {
    var b = brief(r);
    return Map.of(
        "id",
        d.id(),
        "revisionId",
        r.id(),
        "type",
        "FashionConcept",
        "slot",
        b.category(),
        "title",
        b.title(),
        "image",
        r.data().path("image").asText(""),
        "color",
        b.color(),
        "fit",
        b.fit());
  }

  Map<String, Object> ownedItem(StudioStore.Document d) {
    if (!d.data().path("confirmed").asBoolean())
      throw new IllegalArgumentException("Confirm your garment before styling.");
    var a = d.data().path("attributes");
    return Map.of(
        "id",
        d.id(),
        "type",
        "OwnedItem",
        "slot",
        a.path("category").asText(),
        "title",
        d.data().path("description").asText(),
        "image",
        d.data().path("image").asText(),
        "color",
        a.path("color").asText(),
        "fit",
        a.path("fit").asText());
  }

  @Transactional
  public StudioStore.Document outfit(String owner, OutfitRequest request) {
    lockOwner(owner);
    quotaDocuments(owner);
    String message =
        request.message() == null || request.message().isBlank()
            ? "class and campus"
            : StyleLibrary.message(request.message());
    var anchor = store.get(owner, request.anchorId());
    Map<String, Object> item =
        anchor.kind().equals("CONCEPT")
            ? conceptItem(anchor, selected(anchor, request.revisionId()))
            : anchor.kind().equals("OWNED") ? ownedItem(anchor) : null;
    if (item == null) throw new IllegalArgumentException("Choose a garment anchor.");
    var pieces = new ArrayList<Map<String, Object>>();
    pieces.add(item);
    String anchorSlot = item.get("slot").toString();
    var slots =
        new ArrayList<>(
            anchorSlot.equals("ONE_PIECE")
                ? List.of("SHOES", "BAG")
                : List.of("TOP", "BOTTOM", "SHOES", "BAG"));
    var missing = new ArrayList<String>();
    for (String slot : slots) {
      if (slot.equals(anchorSlot)) continue;
      var candidates =
          discover(owner, slot).stream()
              .filter(d -> !d.id().equals(anchor.id()))
              .filter(d -> latest(d).data().path("state").asText().equals("READY"))
              .toList();
      if (candidates.isEmpty()) {
        missing.add(slot);
        continue;
      }
      var choice =
          candidates.stream()
              .max(Comparator.comparingInt(d -> outfitScore(brief(latest(d)), item, message)))
              .orElseThrow();
      pieces.add(conceptItem(choice, latest(choice)));
    }
    UUID id = store.create(owner, "OUTFIT", Map.of("type", "Outfit", "anchorId", anchor.id()));
    store.append(
        id,
        null,
        Map.of(
            "items",
            pieces,
            "anchorId",
            anchor.id(),
            "occasion",
            message,
            "missingSlots",
            missing,
            "explanation",
            "A limited curated capsule balances soft campus colors with light denim and cream"
                + " finishing pieces. Your anchor is preserved. Concepts are styling inspiration,"
                + " with no retail price or availability guarantee."));
    return store.get(owner, id);
  }

  private int outfitScore(StyleLibrary.Brief b, Map<String, Object> anchor, String message) {
    int score = 0;
    String color = anchor.get("color").toString();
    if (b.color().contains("cream") || b.color().contains("white") || b.garment().equals("jeans"))
      score += 3;
    if (color.contains("blue") && b.color().contains("pink")
        || color.contains("pink") && b.color().contains("blue")) score += 2;
    if (message.toLowerCase(Locale.ROOT).contains("skirt") && b.garment().equals("skirt"))
      score += 6;
    if (message.toLowerCase(Locale.ROOT).contains("casual") && b.garment().equals("jeans"))
      score += 4;
    return score;
  }

  @Transactional
  public StudioStore.Document replace(String owner, UUID id, ReplaceRequest request) {
    var d = store.get(owner, id);
    if (d.kind().equals("OUTFIT") && !isOwned(owner, id)) {
      quotaDocuments(owner);
      id = store.create(owner, "OUTFIT", d.data());
      store.append(id, null, latest(d).data());
      d = store.get(owner, id);
    }
    store.writable(owner, id);
    if (!d.kind().equals("OUTFIT")) throw new IllegalArgumentException("Select an outfit.");
    var old = latest(d);
    var anchorId = UUID.fromString(d.data().path("anchorId").asText());
    var next = store.get(owner, request.conceptId());
    if (!next.kind().equals("CONCEPT")) throw new IllegalArgumentException("Choose a concept.");
    var replacement = conceptItem(next, latest(next));
    if (!replacement.get("slot").equals(request.slot()))
      throw new IllegalArgumentException("Replacement must use the same outfit slot.");
    if (replacement.get("id").equals(anchorId))
      throw new IllegalArgumentException("Choose a different complementary piece.");
    var data = (tools.jackson.databind.node.ObjectNode) old.data().deepCopy();
    var items = store.mapper().createArrayNode();
    boolean found = false;
    for (var item : old.data().path("items")) {
      if (item.path("slot").asText().equals(request.slot())) {
        if (item.path("id").asText().equals(anchorId.toString()))
          throw new IllegalArgumentException(
              "The anchor is locked. Start a new outfit to change it.");
        items.add(store.mapper().valueToTree(replacement));
        found = true;
      } else items.add(item);
    }
    if (!found) throw new IllegalArgumentException("That slot is not present in this outfit.");
    data.set("items", items);
    store.append(id, old.id(), data);
    return store.get(owner, id);
  }

  @Transactional
  public void delete(String owner, UUID id) throws IOException {
    lockOwner(owner);
    var prefs = preferences(owner);
    store.writable(owner, id);
    var d = store.get(owner, id);
    var refs = new HashSet<String>();
    if (d.kind().equals("OWNED")) refs.add(d.data().path("image").asText());
    for (var r : d.revisions()) refs.add(r.data().path("image").asText(""));
    // Delete dependent outfits too, so a private source photo cannot remain referenced in saved
    // boards.
    if (d.kind().equals("OWNED") || d.kind().equals("CONCEPT"))
      store
          .jdbc()
          .update(
              "DELETE FROM studio_documents WHERE owner=? AND kind='OUTFIT' AND"
                  + " payload->>'anchorId'=?",
              owner,
              id.toString());
    store.jdbc().update("DELETE FROM studio_documents WHERE id=? AND owner=?", id, owner);
    if (prefs.saved().contains(id) || prefs.hidden().contains(id))
      preferences(
          owner,
          new Preferences(
              prefs.aesthetics(),
              prefs.colors(),
              prefs.saved().stream().filter(v -> !v.equals(id)).toList(),
              prefs.hidden().stream().filter(v -> !v.equals(id)).toList(),
              prefs.lifestyle(),
              prefs.budget()));
    for (String ref : refs)
      if (ref.startsWith("/api/studio/assets/"))
        assets.delete(owner, UUID.fromString(ref.substring(ref.lastIndexOf('/') + 1)));
  }
}
