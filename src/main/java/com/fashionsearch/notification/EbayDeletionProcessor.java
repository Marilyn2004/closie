package com.fashionsearch.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EbayDeletionProcessor {
  private final JdbcTemplate jdbc;

  public EbayDeletionProcessor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private EbayAccountPolicy accounts;

  @Transactional
  public void process(String notificationId) {
    process(notificationId, "", java.util.List.of());
  }

  @Transactional
  public void process(String notificationId, String username, java.util.List<String> aliases) {
    // Same database row lock as ingestion: no page can restore data after a purge.
    jdbc.queryForObject(
        "SELECT imports_paused FROM ebay_catalog_control WHERE id=1 FOR UPDATE", Boolean.class);
    String digest = hash(notificationId);
    int inserted =
        jdbc.update(
            "INSERT INTO ebay_deletion_receipts(notification_hash,purged_products) VALUES (?,0) ON"
                + " CONFLICT DO NOTHING",
            digest);
    if (inserted == 0) return;
    boolean scoped =
        accounts != null
            && accounts.enabled()
            && username != null
            && !username.isBlank()
            && username.length() <= 2048;
    int removed;
    if (scoped) {
      removed = accounts.deleteSeller(username, aliases);
      jdbc.update(
          "UPDATE ebay_deletion_receipts SET handling_mode='SELLER_SCOPED' WHERE"
              + " notification_hash=?",
          digest);
    } else {
      removed = jdbc.update("DELETE FROM products WHERE source='ebay'");
      jdbc.update(
          "UPDATE ebay_catalog_control SET imports_paused=TRUE,pause_reason='IDENTITY_MISSING'"
              + " WHERE id=1");
    }
    jdbc.update(
        "UPDATE ebay_deletion_receipts SET purged_products=? WHERE notification_hash=?",
        removed,
        digest);
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info(
            "ebay_deletion processed mode={} purged_products={}",
            scoped ? "SELLER_SCOPED" : "CONSERVATIVE_PAUSE",
            removed);
  }
}
