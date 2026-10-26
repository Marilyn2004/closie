package com.fashionsearch.service;

import com.fashionsearch.config.EbayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "closie.refresh.enabled", havingValue = "true")
public class CatalogRefreshJob {
  private final ProductIngestionService ingestion;
  private final EbayProperties properties;

  public CatalogRefreshJob(ProductIngestionService ingestion, EbayProperties properties) {
    this.ingestion = ingestion;
    this.properties = properties;
  }

  @Scheduled(
      initialDelay = 60000,
      fixedDelayString =
          "#{T(java.lang.Math).max(3600000, ${closie.refresh.interval-ms:14400000})}")
  public void refresh() {
    if (properties.getClientId().isBlank() || properties.getClientSecret().isBlank()) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("catalog_refresh skipped reason=credentials_missing");
      return;
    }
    try {
      ingestion.importFashionProducts();
    } catch (ImportBusyException running) {
      /* The current import already owns refresh. */
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("catalog_refresh failed reason={}", failure.getClass().getSimpleName());
    }
  }
}
