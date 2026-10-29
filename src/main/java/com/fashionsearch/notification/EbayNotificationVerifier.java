package com.fashionsearch.notification;

import com.fashionsearch.client.EbayClient;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "closie.ebay-notifications.enabled", havingValue = "true")
public class EbayNotificationVerifier {
  private final EbayClient client;
  private final JsonMapper json = JsonMapper.builder().build();
  private final Map<String, CachedKey> cache = new LinkedHashMap<>();
  private Instant nextLookup = Instant.EPOCH;

  record CachedKey(PublicKey key, String signatureAlgorithm, Instant expires) {}

  public EbayNotificationVerifier(EbayClient client) {
    this.client = client;
  }

  public boolean verify(byte[] raw, byte[] compactJson, String header) {
    if (header == null || header.length() > 4096) return false;
    String kid;
    byte[] signature;
    try {
      var h = json.readTree(Base64.getDecoder().decode(header));
      kid = h.path("kid").asText("");
      if (!kid.matches("[A-Za-z0-9_-]{1,256}")) return false;
      signature = Base64.getDecoder().decode(h.path("signature").asText(""));
      if (signature.length == 0 || signature.length > 256) return false;
    } catch (RuntimeException ex) {
      return false;
    }
    CachedKey key = key(kid);
    try {
      Signature verifier = Signature.getInstance(key.signatureAlgorithm());
      verifier.initVerify(key.key());
      verifier.update(raw);
      if (verifier.verify(signature)) return true;
      // eBay's SDK signs the compact JSON serialization, preserving field order.
      verifier.initVerify(key.key());
      verifier.update(compactJson);
      return verifier.verify(signature);
    } catch (GeneralSecurityException ex) {
      return false;
    }
  }

  private synchronized CachedKey key(String kid) {
    var cached = cache.get(kid);
    if (cached != null && cached.expires().isAfter(Instant.now())) return cached;
    if (Instant.now().isBefore(nextLookup)) throw unavailable();
    nextLookup = Instant.now().plusSeconds(5);
    try {
      var response = client.notificationPublicKey(kid);
      if (response == null || !"ECDSA".equalsIgnoreCase(response.algorithm())) throw unavailable();
      String digest = response.digest().replace("-", "").toUpperCase(Locale.ROOT);
      if (!Set.of("SHA1", "SHA256").contains(digest)) throw unavailable();
      String encoded =
          response
              .key()
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s", "");
      var publicKey =
          KeyFactory.getInstance("EC")
              .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
      var result = new CachedKey(publicKey, digest + "withECDSA", Instant.now().plusSeconds(3600));
      if (cache.size() >= 16) cache.remove(cache.keySet().iterator().next());
      cache.put(kid, result);
      return result;
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "eBay verification temporarily unavailable");
  }
}
