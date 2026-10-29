package com.fashionsearch.notification;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "closie.ebay-notifications.enabled", havingValue = "true")
public record EbayNotificationConfig(String endpoint, String verificationToken) {
  public static final String PATH = "/api/ebay/account-deletion";

  public EbayNotificationConfig(
      @Value("${closie.ebay-notifications.endpoint:}") String endpoint,
      @Value("${closie.ebay-notifications.verification-token:}") String verificationToken) {
    URI uri;
    try {
      uri = URI.create(endpoint);
    } catch (RuntimeException ex) {
      throw invalid();
    }
    String host = uri.getHost();
    if (!"https".equals(uri.getScheme())
        || host == null
        || !host.contains(".")
        || host.matches("[0-9.]+")
        || host.contains(":")
        || host.endsWith(".local")
        || host.endsWith(".localhost")
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || !PATH.equals(uri.getPath())
        || verificationToken == null
        || !verificationToken.matches("[A-Za-z0-9_-]{32,80}")) throw invalid();
    this.endpoint = endpoint;
    this.verificationToken = verificationToken;
  }

  private static IllegalStateException invalid() {
    return new IllegalStateException(
        "eBay notifications require a public HTTPS endpoint ending in "
            + PATH
            + " and a 32-80 character verification token.");
  }

  @Override
  public String toString() {
    return "EbayNotificationConfig[redacted]";
  }
}
