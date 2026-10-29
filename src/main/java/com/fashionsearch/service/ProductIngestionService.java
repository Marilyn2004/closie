package com.fashionsearch.service;

import com.fashionsearch.catalog.SourceProduct;
import com.fashionsearch.client.EbayClient;
import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class ProductIngestionService {

  public static final String EBAY_SOURCE = "ebay";

  private final com.fashionsearch.catalog.CatalogNormalizer normalizer =
      new com.fashionsearch.catalog.CatalogNormalizer();

  private static final int TITLE_MAX_LENGTH = 500;

  private static final int EXTERNAL_ID_MAX_LENGTH = 255;

  private static final int CATEGORY_MAX_LENGTH = 255;

  private static final int MAX_EBAY_PAGE_SIZE = 200;

  private final java.util.List<com.fashionsearch.catalog.ProductSource> sources;

  private final EbayProperties ebayProperties;

  private final ProductRepository productRepository;

  private final TransactionOperations transactions;

  public ProductIngestionService(
      EbayClient ebayClient,
      EbayProperties ebayProperties,
      ProductRepository productRepository,
      TransactionOperations transactions) {
    this.sources = java.util.List.of(new com.fashionsearch.catalog.EbayProductSource(ebayClient));
    this.ebayProperties = ebayProperties;
    this.productRepository = productRepository;
    this.transactions = transactions;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ProductIngestionService(
      java.util.List<com.fashionsearch.catalog.ProductSource> sources,
      EbayProperties properties,
      ProductRepository repository,
      TransactionOperations transactions) {
    this.sources = java.util.List.copyOf(sources);
    this.ebayProperties = properties;
    this.productRepository = repository;
    this.transactions = transactions;
  }

  private final java.util.concurrent.locks.ReentrantLock importLock =
      new java.util.concurrent.locks.ReentrantLock();

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.jdbc.core.JdbcTemplate importHistory;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.fashionsearch.notification.EbayAccountPolicy accounts;

  public ImportResult importFashionProducts() {
    if (!importLock.tryLock()) throw new ImportBusyException();
    Long runId = null;
    try {
      if (importHistory != null
          && Boolean.TRUE.equals(
              importHistory.queryForObject(
                  "SELECT imports_paused FROM ebay_catalog_control WHERE id=1", Boolean.class)))
        throw new IllegalStateException(
            "eBay imports are paused after an account deletion notification; operator review"
                + " required");
      if (importHistory != null)
        runId =
            importHistory.queryForObject(
                "INSERT INTO catalog_import_runs(status) VALUES ('RUNNING') RETURNING id",
                Long.class);
      var result = performImport();
      if (runId != null)
        importHistory.update(
            "UPDATE catalog_import_runs SET"
                + " finished_at=CURRENT_TIMESTAMP,status='SUCCEEDED',created_count=?,updated_count=?,skipped_count=?"
                + " WHERE id=?",
            result.created(),
            result.updated(),
            result.skipped(),
            runId);
      org.slf4j.LoggerFactory.getLogger(getClass())
          .info(
              "catalog_import status=SUCCEEDED created={} updated={} skipped={}",
              result.created(),
              result.updated(),
              result.skipped());
      return result;
    } catch (RuntimeException failure) {
      if (runId != null)
        importHistory.update(
            "UPDATE catalog_import_runs SET"
                + " finished_at=CURRENT_TIMESTAMP,status='FAILED',error_code=? WHERE id=?",
            failure.getClass().getSimpleName(),
            runId);
      throw failure;
    } finally {
      importLock.unlock();
    }
  }

  private ImportResult performImport() {
    int pageSize = ebayProperties.getPageSize();
    int maxPages = ebayProperties.getMaxPages();
    if (pageSize < 1 || pageSize > MAX_EBAY_PAGE_SIZE || maxPages < 1) {
      throw new IllegalStateException(
          "eBay page size must be from 1 to 200 and max pages must be at least 1");
    }

    int created = 0;
    int updated = 0;
    int skipped = 0;
    for (var source : sources) {
      for (String keyword : ebayProperties.getSearchKeywords()) {
        if (keyword == null || keyword.isBlank()) {
          continue;
        }
        for (int page = 0; page < maxPages; page++) {
          var searchPage = source.fetch(keyword, page * pageSize);
          ImportResult pageResult =
              transactions.execute(status -> persist(source.source(), searchPage.products()));
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
    }
    return new ImportResult(created, updated, skipped);
  }

  private ImportResult persist(
      String source, List<com.fashionsearch.catalog.SourceProduct> ebayProducts) {
    if (EBAY_SOURCE.equals(source) && importHistory != null) {
      Boolean paused =
          importHistory.queryForObject(
              "SELECT imports_paused FROM ebay_catalog_control WHERE id=1 FOR UPDATE",
              Boolean.class);
      if (Boolean.TRUE.equals(paused))
        throw new IllegalStateException(
            "eBay imports are paused after an account deletion notification; operator review"
                + " required");
    }
    if (EBAY_SOURCE.equals(source) && accounts != null && accounts.enabled())
      accounts.lockAndValidateKey();
    int created = 0;
    int updated = 0;
    int skipped = 0;
    for (var item : ebayProducts) {
      Optional<Product> mapped = map(item);
      if (mapped.isEmpty()) {
        skipped++;
        continue;
      }
      Product incoming = mapped.get();
      Optional<Product> existing =
          productRepository.findBySourceAndExternalId(source, incoming.getExternalId());
      incoming.setSource(source);
      if (EBAY_SOURCE.equals(source)
          && accounts != null
          && accounts.enabled()
          && !accounts.allow(incoming, item.ownerIdentifier())) {
        skipped++;
        continue;
      }
      normalizer.enrich(incoming, item);
      incoming.setLastSeenAt(java.time.Instant.now());
      if (existing.isEmpty()) {
        productRepository.saveAndFlush(incoming);
        created++;
        continue;
      }
      Product current = existing.get();
      boolean metadataChanged =
          !Objects.equals(current.getEbayOwnerHash(), incoming.getEbayOwnerHash())
              || !Objects.equals(current.getOutfitSlot(), incoming.getOutfitSlot())
              || !Objects.equals(current.getRetailer(), incoming.getRetailer())
              || !Objects.equals(current.getBrand(), incoming.getBrand())
              || !Objects.equals(current.getColor(), incoming.getColor())
              || !Objects.equals(current.getAvailableSizes(), incoming.getAvailableSizes())
              || !Objects.equals(current.getAvailability(), incoming.getAvailability())
              || !Objects.equals(current.getStyleTags(), incoming.getStyleTags())
              || !Objects.equals(current.getOccasionTags(), incoming.getOccasionTags());
      current.setEbayOwnerHash(incoming.getEbayOwnerHash());
      current.setLastSeenAt(incoming.getLastSeenAt());
      current.setOutfitSlot(incoming.getOutfitSlot());
      current.setRetailer(incoming.getRetailer());
      current.setBrand(incoming.getBrand());
      current.setColor(incoming.getColor());
      current.setAvailableSizes(incoming.getAvailableSizes());
      current.setAvailability(incoming.getAvailability());
      current.setStyleTags(incoming.getStyleTags());
      current.setOccasionTags(incoming.getOccasionTags());
      if (applyChanges(current, incoming) || metadataChanged) {
        productRepository.saveAndFlush(current);
        updated++;
      }
    }
    return new ImportResult(created, updated, skipped);
  }

  private Optional<Product> map(SourceProduct ebayProduct) {
    if (ebayProduct == null) {
      return Optional.empty();
    }
    String externalId = trimToNull(ebayProduct.externalId());
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
    product.setDescription(trimToNull(ebayProduct.description()));
    product.setCategory(normalizeCategory(ebayProduct.category()));
    product.setPrice(price);
    product.setCurrency(currency);
    product.setImageUrl(trimToNull(ebayProduct.imageUrl()));
    product.setProductUrl(trimToNull(ebayProduct.productUrl()));
    return Optional.of(product);
  }

  private boolean applyChanges(Product current, Product incoming) {
    boolean changed =
        !Objects.equals(current.getTitle(), incoming.getTitle())
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
    if (price.signum() < 0 || scaled.signum() < 0) {
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
    return trimmed.matches("[A-Za-z]{3}") ? trimmed.toUpperCase(java.util.Locale.ROOT) : null;
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
