package com.fashionsearch.catalog;

import static org.assertj.core.api.Assertions.*;

import com.fashionsearch.model.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogNormalizerTest {
  @Test
  void classifiesOutfitSlots() {
    assertThat(CatalogNormalizer.slot("Wool cardigan", "Sweaters")).isEqualTo("TOP");
    assertThat(CatalogNormalizer.slot("White sneakers", "Shoes")).isEqualTo("SHOES");
    assertThat(CatalogNormalizer.slot("Midi dress", null)).isEqualTo("ONE_PIECE");
    assertThat(CatalogNormalizer.slot("Tailored dress pants", "Pants")).isEqualTo("BOTTOM");
    assertThat(CatalogNormalizer.slot("Silk dress shirt", "Tops")).isEqualTo("TOP");
    assertThat(CatalogNormalizer.slot("Sweater dress", "Dresses")).isEqualTo("ONE_PIECE");
    assertThat(CatalogNormalizer.slot("Silver necklace", null)).isEqualTo("ACCESSORY");
  }

  @Test
  void preservesUnknownProviderFacts() {
    Product p = new Product();
    new CatalogNormalizer()
        .enrich(
            p,
            new SourceProduct(
                "id",
                "Black shirt",
                null,
                null,
                BigDecimal.TEN,
                "USD",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    assertThat(p.getBrand()).isNull();
    assertThat(p.getColor()).isNull();
    assertThat(p.getAvailability()).isEqualTo("UNKNOWN");
    assertThat(p.getAvailableSizes()).isNull();
  }
}
