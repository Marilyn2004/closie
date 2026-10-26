package com.fashionsearch.catalog;

import com.fashionsearch.client.EbayClient;
import org.springframework.stereotype.Component;

@Component
public class EbayProductSource implements ProductSource {
  private final EbayClient client;

  public EbayProductSource(EbayClient client) {
    this.client = client;
  }

  public String source() {
    return "ebay";
  }

  @org.springframework.beans.factory.annotation.Autowired
  private com.fashionsearch.config.EbayProperties properties;

  public Page fetch(String keyword, int offset) {
    var page = client.search(keyword, offset);
    java.util.concurrent.atomic.AtomicInteger detailCount =
        new java.util.concurrent.atomic.AtomicInteger();
    return new Page(
        page.products().stream()
            .map(
                p ->
                    properties != null
                            && properties.isEnrichDetails()
                            && detailCount.getAndIncrement() < properties.getDetailsPerPage()
                        ? client.enrich(p)
                        : p)
            .map(
                p ->
                    new SourceProduct(
                        p.itemId(),
                        p.title(),
                        p.shortDescription(),
                        p.categoryName(),
                        p.price(),
                        p.currency(),
                        p.imageUrl(),
                        p.itemWebUrl(),
                        p.brand(),
                        "eBay",
                        null,
                        p.color(),
                        p.sizes(),
                        p.availability(),
                        null,
                        null))
            .toList(),
        page.hasNext());
  }
}
