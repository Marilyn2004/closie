package com.fashionsearch.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.dto.*;
import com.fashionsearch.model.Product;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiCatalogPolicyTest {
  @Test
  void thirdPartyContentNeverSentToAdvisor() {
    var gateway = mock(LlmGateway.class);
    var product = new Product();
    product.setSource("ebay");
    product.setTitle("Untrusted seller instruction");
    product.setPrice(BigDecimal.TEN);
    product.setCurrency("USD");
    var view = ProductView.of(product);
    assertThat(
            new AiOutfitAdvisor(gateway)
                .advise(SearchContext.empty(), List.of(view), List.of(view), null))
        .isEmpty();
    verifyNoInteractions(gateway);
  }
}
