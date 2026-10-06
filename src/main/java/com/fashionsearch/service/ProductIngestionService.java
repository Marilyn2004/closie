package com.fashionsearch.service;

import com.fashionsearch.client.EbayClient;
import com.fashionsearch.client.EbaySearchPage;
import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.EbayProduct;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProductIngestionService {

    public static final String EBAY_SOURCE = "ebay";

    private static final int TITLE_MAX_LENGTH = 500;

    private static final int EXTERNAL_ID_MAX_LENGTH = 255;

    private static final int CATEGORY_MAX_LENGTH = 255;

    private static final int MAX_EBAY_PAGE_SIZE = 200;

    private final EbayClient ebayClient;

    private final EbayProperties ebayProperties;

    private final ProductRepository productRepository;

    private final TransactionOperations transactions;

    public ProductIngestionService(
            EbayClient ebayClient,
            EbayProperties ebayProperties,
            ProductRepository productRepository,
            TransactionOperations transactions
    ) {
        this.ebayClient = ebayClient;
        this.ebayProperties = ebayProperties;
        this.productRepository = productRepository;
        this.transactions = transactions;
    }

    public ImportResult importFashionProducts() {
        int pageSize = ebayProperties.getPageSize();
        int maxPages = ebayProperties.getMaxPages();
        if (pageSize < 1 || pageSize > MAX_EBAY_PAGE_SIZE || maxPages < 1) {
            throw new IllegalStateException("eBay page size must be from 1 to 200 and max pages must be at least 1");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (String keyword : ebayProperties.getSearchKeywords()) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            for (int page = 0; page < maxPages; page++) {
                EbaySearchPage searchPage = ebayClient.search(keyword, page * pageSize);
                ImportResult pageResult = transactions.execute(status -> persist(searchPage.products()));
                if (pageResult == null) {
                    throw new IllegalStateException("product import transaction did not complete");
                }
                created += pageResult.created();
                updated += pageResult.updated();
                skipped += pageResult.skipped();
                if (!searchPage.hasNext()) {
                    break;
                }
            }
        }
        return new ImportResult(created, updated, skipped);
    }

    private ImportResult persist(List<EbayProduct> ebayProducts) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (EbayProduct ebayProduct : ebayProducts) {
            Optional<Product> mapped = map(ebayProduct);
            if (mapped.isEmpty()) {
                skipped++;
                continue;
            }
            Product incoming = mapped.get();
            Optional<Product> existing = productRepository.findBySourceAndExternalId(
                    EBAY_SOURCE,
                    incoming.getExternalId()
            );
            if (existing.isEmpty()) {
                productRepository.saveAndFlush(incoming);
                created++;
                continue;
            }
            Product current = existing.get();
            if (applyChanges(current, incoming)) {
                productRepository.saveAndFlush(current);
                updated++;
            }
        }
        return new ImportResult(created, updated, skipped);
    }

    private Optional<Product> map(EbayProduct ebayProduct) {
        if (ebayProduct == null) {
            return Optional.empty();
        }
        String externalId = trimToNull(ebayProduct.itemId());
        String title = trimToNull(ebayProduct.title());
        if (externalId == null || externalId.length() > EXTERNAL_ID_MAX_LENGTH) {
            return Optional.empty();
        }
        if (title == null || title.length() > TITLE_MAX_LENGTH) {
            return Optional.empty();
        }
        BigDecimal price = normalizePrice(ebayProduct.price());
        String currency = normalizeCurrency(ebayProduct.currency());
        if (price == null || currency == null) {
            return Optional.empty();
        }

        Product product = new Product();
        product.setSource(EBAY_SOURCE);
        product.setExternalId(externalId);
        product.setTitle(title);
        product.setDescription(trimToNull(ebayProduct.shortDescription()));
        product.setCategory(normalizeCategory(ebayProduct.categoryName()));
        product.setPrice(price);
        product.setCurrency(currency);
        product.setImageUrl(trimToNull(ebayProduct.imageUrl()));
        product.setProductUrl(trimToNull(ebayProduct.itemWebUrl()));
        return Optional.of(product);
    }

    private boolean applyChanges(Product current, Product incoming) {
        boolean changed = !Objects.equals(current.getTitle(), incoming.getTitle())
                || !Objects.equals(current.getDescription(), incoming.getDescription())
                || !Objects.equals(current.getCategory(), incoming.getCategory())
                || current.getPrice().compareTo(incoming.getPrice()) != 0
                || !Objects.equals(current.getCurrency(), incoming.getCurrency())
                || !Objects.equals(current.getImageUrl(), incoming.getImageUrl())
                || !Objects.equals(current.getProductUrl(), incoming.getProductUrl());
        if (!changed) {
            return false;
        }
        current.setTitle(incoming.getTitle());
        current.setDescription(incoming.getDescription());
        current.setCategory(incoming.getCategory());
        current.setPrice(incoming.getPrice());
        current.setCurrency(incoming.getCurrency());
        current.setImageUrl(incoming.getImageUrl());
        current.setProductUrl(incoming.getProductUrl());
        return true;
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        BigDecimal scaled = price.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() < 0) {
            return null;
        }
        int integerDigits = Math.max(0, scaled.precision() - scaled.scale());
        if (integerDigits > 10) {
            return null;
        }
        return scaled;
    }

    private String normalizeCurrency(String currency) {
        String trimmed = trimToNull(currency);
        if (trimmed == null || trimmed.length() != 3) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    private String normalizeCategory(String category) {
        String trimmed = trimToNull(category);
        if (trimmed == null || trimmed.length() > CATEGORY_MAX_LENGTH) {
            return null;
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
