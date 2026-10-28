package com.fashionsearch.controller;

import com.fashionsearch.dto.SearchContext;
import com.fashionsearch.service.CatalogService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogStatusController {
  private final CatalogService catalog;
  private final boolean demo, ai;
  private final String key, model;

  public CatalogStatusController(
      CatalogService catalog,
      @Value("${closie.demo.enabled:false}") boolean demo,
      @Value("${closie.ai.enabled:false}") boolean ai,
      @Value("${closie.ai.key:}") String key,
      @Value("${closie.ai.model:}") String model) {
    this.catalog = catalog;
    this.demo = demo;
    this.ai = ai;
    this.key = key;
    this.model = model;
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.fashionsearch.demo.PublicDemoPolicy demoPolicy;

  @GetMapping("/api/status")
  public Map<String, Object> status() {
    if (demoPolicy != null && demoPolicy.enabled())
      return Map.of(
          "catalogMode",
          "public-demo",
          "aiConfigured",
          false,
          "eligibleProducts",
          0,
          "notice",
          "Pre-generated AI fashion inspiration; no live generation or shopping availability.");
    return Map.of(
        "catalogMode",
        demo ? "demo" : "live",
        "aiConfigured",
        ai && !key.isBlank() && !model.isBlank(),
        "eligibleProducts",
        catalog.search(SearchContext.empty(), 0).total(),
        "notice",
        demo
            ? "Sample collection · Illustrations and prices are fictional. Not for sale."
            : "Prices and availability may change. Confirm with the retailer.");
  }
}
