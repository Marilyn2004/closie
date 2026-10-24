package com.fashionsearch.controller;

import com.fashionsearch.dto.*;
import com.fashionsearch.service.*;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
  private final CatalogService catalog;
  private final QueryInterpreter interpreter;
  private final OutfitService outfits;

  public CatalogController(
      CatalogService catalog, QueryInterpreter interpreter, OutfitService outfits) {
    this.catalog = catalog;
    this.interpreter = interpreter;
    this.outfits = outfits;
  }

  public record SearchResponse(
      SearchContext context, String interpretationMode, CatalogService.Results results) {}

  @PostMapping("/search")
  public SearchResponse search(@RequestBody SearchRequest request) {
    var parsed = interpreter.interpret(request);
    return new SearchResponse(
        parsed.context(),
        parsed.mode(),
        catalog.search(parsed.context(), request.page() == null ? 0 : request.page()));
  }

  @GetMapping("/search")
  public SearchResponse searchGet(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "USD") String currency,
      @RequestParam(defaultValue = "0") int page) {
    return search(
        new SearchRequest(
            q,
            null,
            new SearchContext(
                null, category, brand, color, minPrice, maxPrice, currency, null, null),
            page));
  }

  @GetMapping("/discover")
  public SearchResponse discover(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(required = false) String aesthetic,
      @RequestParam(defaultValue = "USD") String currency,
      @RequestParam(defaultValue = "0") int page) {
    SearchContext c =
        new SearchContext("", category, null, null, null, maxPrice, currency, aesthetic, null);
    interpreter.validate(c);
    return new SearchResponse(c, "rules", catalog.search(c, page));
  }

  @GetMapping("/products/{id}")
  public ProductView product(@PathVariable long id) {
    return catalog
        .product(id)
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Product unavailable"));
  }

  @PostMapping("/style")
  public OutfitService.Outfit style(@RequestBody OutfitService.Request request) {
    return outfits.style(request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
  }
}
