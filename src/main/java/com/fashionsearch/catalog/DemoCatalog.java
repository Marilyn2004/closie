package com.fashionsearch.catalog;

import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "closie.demo.enabled", havingValue = "true")
public class DemoCatalog implements ApplicationRunner {
  private final ProductRepository products;
  private final JdbcTemplate jdbc;

  public DemoCatalog(ProductRepository products, JdbcTemplate jdbc) {
    this.products = products;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!"closie_demo".equals(jdbc.queryForObject("select current_database()", String.class)))
      throw new IllegalStateException(
          "Demo seeding requires the isolated closie_demo database. Use ./dev demo.");
    for (String row : ITEMS) {
      String[] f = row.split("\\|", -1);
      String id = f[0];
      if (products.findBySourceAndExternalId("closie-demo", id).isPresent()) continue;
      Product p = new Product();
      p.setSource("closie-demo");
      p.setExternalId(id);
      p.setTitle(f[1]);
      p.setOutfitSlot(f[2]);
      p.setCategory(f[2]);
      p.setColor(f[3]);
      p.setPrice(new BigDecimal(f[4]));
      p.setCurrency(f[5]);
      p.setBrand(f[6]);
      p.setRetailer("Closie Sample Studio");
      p.setDescription(f[7] + ". Fictional sample design, not a retailer listing.");
      p.setStyleTags(f[7]);
      p.setOccasionTags(f[8]);
      p.setAvailableSizes("Sample sizes XS,S,M,L");
      p.setImageUrl("/demo/" + id + ".svg");
      p.setProductUrl(null);
      p.setAvailability("UNKNOWN");
      p.setLastSeenAt(Instant.now());
      products.save(p);
    }
  }

  private static final List<String> ITEMS =
      List.of(
          "ivory-knit|The soft rib cardigan|TOP|ivory|32|USD|Closie Studio|feminine effortless"
              + " minimal soft aritzia|coffee date college weekend",
          "blue-shirt|The relaxed poplin shirt|TOP|blue|28|USD|Closie Studio|classic effortless"
              + " minimal relaxed|college work weekend",
          "pink-blouse|The ribbon blouse|TOP|pink|36|USD|Closie Atelier|feminine playful"
              + " romantic|coffee date dinner date",
          "black-top|The square-neck knit|TOP|black|25|USD|Closie Studio|feminine minimal aritzia"
              + " sleek|dinner date college",
          "cream-tee|The everyday cotton tee|TOP|cream|18|USD|Closie Everyday|casual relaxed"
              + " effortless|college weekend coffee date",
          "olive-vest|The tailored waistcoat|TOP|green|39|USD|Closie Atelier|classic minimal"
              + " tailored|work dinner date",
          "blue-jeans|The straight-leg denim|BOTTOM|blue|39|USD|Closie Everyday|casual effortless"
              + " relaxed playful|college coffee date weekend",
          "ivory-trousers|The fluid tailored trouser|BOTTOM|ivory|42|USD|Closie Studio|minimal"
              + " effortless aritzia|work college coffee date",
          "black-skirt|The satin midi skirt|BOTTOM|black|35|USD|Closie Atelier|feminine romantic"
              + " sleek|dinner date coffee date",
          "beige-shorts|The city tailored short|BOTTOM|beige|29|USD|Closie Everyday|casual minimal"
              + " playful|college weekend",
          "pink-dress|The gathered midi dress|ONE_PIECE|pink|59|USD|Closie Atelier|feminine"
              + " romantic playful|coffee date dinner date wedding",
          "black-dress|The understated slip dress|ONE_PIECE|black|65|USD|Closie Studio|feminine"
              + " minimal sleek aritzia|dinner date party",
          "cream-dress|The linen day dress|ONE_PIECE|cream|54|USD|Closie Everyday|effortless casual"
              + " romantic|coffee date weekend",
          "olive-blazer|The softly structured blazer|OUTERWEAR|green|58|USD|Closie Studio|minimal"
              + " classic aritzia|work dinner date",
          "beige-jacket|The cropped utility jacket|OUTERWEAR|beige|49|USD|Closie Everyday|casual"
              + " playful relaxed|college weekend",
          "black-jacket|The leather-look cropped jacket|OUTERWEAR|black|79|USD|Closie Atelier|edgy"
              + " sleek minimal|dinner date weekend",
          "white-sneakers|The clean court sneaker|SHOES|white|38|USD|Closie Everyday|casual minimal"
              + " effortless|college coffee date weekend",
          "black-flats|The modern ballet flat|SHOES|black|34|USD|Closie Studio|feminine minimal"
              + " classic aritzia|coffee date college work dinner date",
          "brown-loafers|The penny loafer|SHOES|brown|45|USD|Closie Studio|classic effortless"
              + " tailored|work college coffee date",
          "cream-heels|The little slingback|SHOES|cream|44|USD|Closie Atelier|feminine romantic"
              + " sleek|dinner date party wedding",
          "brown-bag|The everyday shoulder bag|BAG|brown|29|USD|Closie Everyday|effortless classic"
              + " relaxed|coffee date college weekend",
          "black-bag|The sculpted mini bag|BAG|black|35|USD|Closie Atelier|minimal playful"
              + " sleek|dinner date party",
          "cream-tote|The canvas carryall|BAG|cream|22|USD|Closie Everyday|casual relaxed"
              + " minimal|college weekend",
          "gold-hoops|The everyday small hoops|ACCESSORY|gold|14|USD|Closie Studio|minimal feminine"
              + " classic|coffee date work dinner date",
          "silver-chain|The delicate chain|ACCESSORY|silver|16|USD|Closie Atelier|feminine sleek"
              + " minimal|coffee date dinner date",
          "pink-scarf|The little neck scarf|ACCESSORY|pink|12|USD|Closie Atelier|playful romantic"
              + " feminine|coffee date weekend",
          "euro-knit|The soft rib cardigan — EUR sample|TOP|ivory|30|EUR|Closie Studio|feminine"
              + " effortless minimal|coffee date college",
          "euro-skirt|The satin midi skirt — EUR sample|BOTTOM|black|33|EUR|Closie Atelier|feminine"
              + " sleek|coffee date dinner date",
          "euro-flats|The modern ballet flat — EUR sample|SHOES|black|32|EUR|Closie Studio|feminine"
              + " minimal|coffee date college");
}
