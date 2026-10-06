package com.fashionsearch.client;

import com.fashionsearch.dto.EbayProduct;

import java.util.List;

public record EbaySearchPage(List<EbayProduct> products, boolean hasNext) {

    public EbaySearchPage {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
