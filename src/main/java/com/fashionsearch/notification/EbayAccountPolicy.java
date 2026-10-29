package com.fashionsearch.notification;

import com.fashionsearch.model.Product;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Opaque seller identifiers are HMACed, never exposed in product responses or logs. */
@Component
public class EbayAccountPolicy {
  private final JdbcTemplate jdbc;
  private final boolean enabled;
  private final String key;

  public EbayAccountPolicy(
      JdbcTemplate jdbc,
      @Value("${closie.ebay-accounts.enabled:false}") boolean enabled,
      @Value("${closie.ebay-accounts.hash-key:}") String key) {
    this.jdbc = jdbc;
    this.enabled = enabled;
    this.key = key;
    if (enabled && !key.matches("[a-fA-F0-9]{64}"))
      throw new IllegalStateException(
          "Seller tracking requires a private 32-byte hex key. Keep the key stable.");
  }

  public boolean enabled() {
    return enabled;
  }

  public String ownerHash(String identifier) {
    if (!enabled || identifier == null || identifier.isBlank() || identifier.length() > 2048)
      throw new IllegalArgumentException("A valid seller identifier is required.");
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(HexFormat.of().parseHex(key), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(identifier.trim().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Must run inside the import/deletion transaction; also serializes key initialization. */
  public void lockAndValidateKey() {
    String stored =
        jdbc.queryForObject(
            "SELECT hash_key_fingerprint FROM ebay_catalog_control WHERE id=1 FOR UPDATE",
            String.class);
    String fingerprint = EbayDeletionProcessor.hash(key.toLowerCase(Locale.ROOT));
    if (stored == null)
      jdbc.update("UPDATE ebay_catalog_control SET hash_key_fingerprint=? WHERE id=1", fingerprint);
    else if (!MessageDigest.isEqual(
        stored.getBytes(StandardCharsets.UTF_8), fingerprint.getBytes(StandardCharsets.UTF_8)))
      throw new IllegalStateException(
          "Seller tracking key changed; imports and deletion require operator review.");
  }

  public boolean allow(Product incoming, String seller) {
    if (seller == null || seller.isBlank() || seller.length() > 2048) return false;
    String hash = ownerHash(seller);
    incoming.setEbayOwnerHash(hash);
    return !Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM ebay_deleted_accounts WHERE owner_hash=?)",
            Boolean.class,
            hash));
  }

  public int deleteSeller(String username, List<String> aliases) {
    lockAndValidateKey();
    var hashes = new LinkedHashSet<String>();
    hashes.add(ownerHash(username));
    for (String alias : aliases)
      if (alias != null && !alias.isBlank() && alias.length() <= 2048) hashes.add(ownerHash(alias));
    for (String hash : hashes)
      jdbc.update(
          "INSERT INTO ebay_deleted_accounts(owner_hash) VALUES (?) ON CONFLICT DO NOTHING", hash);
    String placeholders = String.join(",", Collections.nCopies(hashes.size(), "?"));
    // Legacy rows lack ownership: purge those conservatively, retain all other sellers.
    return jdbc.update(
        "DELETE FROM products WHERE source='ebay' AND (ebay_owner_hash IS NULL OR ebay_owner_hash"
            + " IN ("
            + placeholders
            + "))",
        hashes.toArray());
  }

  @Transactional
  public void resumeReviewedEmptyCatalog() {
    if (!enabled) throw conflict();
    lockAndValidateKey();
    var control =
        jdbc.queryForMap("SELECT imports_paused,pause_reason FROM ebay_catalog_control WHERE id=1");
    if (Boolean.FALSE.equals(control.get("imports_paused"))) return;
    if (!"LEGACY_EMPTY_REVIEW".equals(control.get("pause_reason"))
        || jdbc.queryForObject("SELECT count(*) FROM products WHERE source='ebay'", Long.class) != 0
        || jdbc.queryForObject(
                "SELECT count(*) FROM ebay_deletion_receipts WHERE handling_mode='LEGACY_PURGE' AND"
                    + " purged_products>0",
                Long.class)
            != 0) throw conflict();
    jdbc.update(
        "UPDATE ebay_catalog_control SET imports_paused=FALSE,pause_reason=NULL WHERE id=1");
  }

  private static ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Resume requires seller tracking and an empty, reviewed legacy catalog. Identity-related"
            + " pauses cannot be bypassed.");
  }
}
