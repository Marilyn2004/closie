package com.fashionsearch.dto;

import java.math.BigDecimal;

public record EbayProduct(
        String itemId,
        String title,
        String shortDescription,
        String categoryName,
        BigDecimal price,
        String currency,
        String imageUrl,
        String itemWebUrl
) {
}
