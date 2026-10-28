package com.fashionsearch.controller;

import com.fashionsearch.config.ImportProperties;
import com.fashionsearch.notification.EbayAccountPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {
  private final JdbcTemplate jdbc;
  private final ImportProperties properties;
  private final EbayAccountPolicy accounts;

  public CatalogAdminController(
      JdbcTemplate jdbc, ImportProperties properties, EbayAccountPolicy accounts) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.accounts = accounts;
  }

  private boolean authorized(String token) {
    String expected = properties.getToken();
    return token != null
        && expected != null
        && !expected.isBlank()
        && MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/status")
  public ResponseEntity<?> status(
      @RequestHeader(value = "X-Import-Token", required = false) String token) {
    if (!authorized(token)) return ResponseEntity.status(401).build();
    var state =
        jdbc.queryForMap("SELECT imports_paused,pause_reason FROM ebay_catalog_control WHERE id=1");
    state.put("sellerTrackingEnabled", accounts.enabled());
    state.put(
        "ebayProducts",
        jdbc.queryForObject("SELECT count(*) FROM products WHERE source='ebay'", Long.class));
    state.put(
        "deletionReceipts",
        jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Long.class));
    state.put(
        "suppressedAccounts",
        jdbc.queryForObject("SELECT count(*) FROM ebay_deleted_accounts", Long.class));
    return ResponseEntity.ok(state);
  }

  public record Review(boolean acknowledgeEmptyCatalogReview) {}

  @PostMapping("/resume-empty-legacy")
  public ResponseEntity<?> resume(
      @RequestHeader(value = "X-Import-Token", required = false) String token,
      @RequestBody Review review) {
    if (!authorized(token)) return ResponseEntity.status(401).build();
    if (!review.acknowledgeEmptyCatalogReview()) return ResponseEntity.badRequest().build();
    accounts.resumeReviewedEmptyCatalog();
    return ResponseEntity.ok(Map.of("reviewed", true));
  }
}
