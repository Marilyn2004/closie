package com.fashionsearch.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoCatalogTest {
  @Test
  void refusesToSeedAnyNonDemoDatabase() {
    var products = mock(ProductRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("select current_database()", String.class))
        .thenReturn("fashion_search");
    assertThatThrownBy(() -> new DemoCatalog(products, jdbc).run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("isolated closie_demo");
    verifyNoInteractions(products);
  }
}
