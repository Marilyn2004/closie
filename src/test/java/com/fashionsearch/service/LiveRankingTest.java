package com.fashionsearch.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.dto.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class LiveRankingTest {
  private ProductView item(long id, String title, String slot, String price) {
    return new ProductView(
        id,
        title,
        null,
        slot,
        null,
        "eBay",
        "ebay",
        slot,
        null,
        null,
        new BigDecimal(price),
        "USD",
        "https://example.com/i",
        "https://example.com/p",
        "UNKNOWN",
        java.time.Instant.now(),
        null,
        null,
        false);
  }

  @Test
  void dinnerRefinementChangesUntypedLiveCatalogAndKeepsBudget() {
    var products =
        List.of(
            item(1, "Cotton Cardigan", "TOP", "10"),
            item(2, "Denim Jeans", "BOTTOM", "15"),
            item(3, "Athletic Sneakers", "SHOES", "15"),
            item(4, "Satin Midi Dress", "ONE_PIECE", "35"),
            item(5, "Leather Loafers", "SHOES", "20"));
    var catalog = mock(CatalogService.class);
    when(catalog.candidates(any())).thenReturn(products);
    when(catalog.matches(anyLong(), any())).thenReturn(true);
    for (var p : products) when(catalog.product(p.id())).thenReturn(Optional.of(p));
    var outfits = new OutfitService(catalog, new QueryInterpreter(new UserQueryAi("", "", false)));
    var coffee =
        outfits.style(
            new OutfitService.Request("Feminine coffee date under $150", null, null, null, false));
    var dinner =
        outfits.style(
            new OutfitService.Request(
                "Actually, make it suitable for a dinner date",
                coffee.context(),
                null,
                null,
                false));
    assertThat(coffee.complete()).isTrue();
    assertThat(dinner.complete()).isTrue();
    assertThat(dinner.total()).isLessThanOrEqualTo(new BigDecimal("150"));
    assertThat(dinner.products()).extracting(ProductView::id).contains(4L, 5L).doesNotContain(3L);
    assertThat(coffee.products())
        .extracting(ProductView::id)
        .isNotEqualTo(dinner.products().stream().map(ProductView::id).toList());
  }
}
