package com.fashionsearch.catalog;

import java.util.List;

/** Implement only for sources with documented ingestion/display permissions. */
public interface ProductSource {
  String source();

  Page fetch(String keyword, int offset);

  record Page(List<SourceProduct> products, boolean hasNext) {
    public Page {
      products = products == null ? List.of() : List.copyOf(products);
    }
  }
}
