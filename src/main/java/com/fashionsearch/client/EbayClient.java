package com.fashionsearch.client;

import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.EbayProduct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EbayClient {

  static final String BROWSE_SCOPE = "https://api.ebay.com/oauth/api_scope";

  static final String FIXED_PRICE_FILTER = "buyingOptions:{FIXED_PRICE}";

  private final EbayProperties properties;

  private final RestClient restClient;

  private String accessToken;

  private Instant tokenExpiresAt = Instant.EPOCH;

  public EbayClient(EbayProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClient =
        restClientBuilder.baseUrl(normalizeBaseUrl(properties.getApiBaseUrl())).build();
  }

  void clearCachedToken() {
    accessToken = null;
    tokenExpiresAt = Instant.EPOCH;
  }

  private Instant retryNotBefore = Instant.EPOCH;

  public synchronized EbaySearchPage search(String keyword, int offset) {
    if (Instant.now().isBefore(retryNotBefore))
      throw new EbayClientException("eBay is in a rate-limit cooldown; refresh later.");
    boolean renewed = false;
    for (int attempt = 0; attempt < 3; attempt++) {
      String token = currentAccessToken();
      try {
        throttle();
        var response =
            restClient
                .get()
                .uri(searchUri(keyword, offset))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-EBAY-C-MARKETPLACE-ID", properties.getMarketplaceId())
                .retrieve()
                .body(SearchResponse.class);
        if (response == null) throw new EbayClientException("eBay search response was empty");
        return new EbaySearchPage(toProducts(response.itemSummaries()), hasText(response.next()));
      } catch (RestClientResponseException failure) {
        int status = failure.getStatusCode().value();
        if (status == 401 && !renewed) {
          clearCachedToken();
          renewed = true;
          continue;
        }
        if (status == 429) {
          long seconds = retryAfter(failure);
          if (attempt < 2 && seconds <= 2) {
            pause(Math.max(1, seconds) * 1000);
            continue;
          }
          retryNotBefore = Instant.now().plusSeconds(Math.max(1, seconds));
          throw new EbayClientException(
              "eBay rate limit reached. Refresh later; Retry-After is respected.");
        }
        if (status >= 500 && attempt < 2) {
          pause(250L * (attempt + 1));
          continue;
        }
        throw new EbayClientException("eBay search failed with HTTP " + status);
      } catch (RestClientException failure) {
        throw new EbayClientException("eBay search failed");
      }
    }
    throw new EbayClientException("eBay search retry limit reached");
  }

  private long lastRequestNanos;

  private void throttle() {
    long elapsed = (System.nanoTime() - lastRequestNanos) / 1000000;
    long wait = properties.getRequestIntervalMillis() - elapsed;
    if (wait > 0) pause(wait);
    lastRequestNanos = System.nanoTime();
  }

  private void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new EbayClientException("eBay request interrupted");
    }
  }

  private long retryAfter(RestClientResponseException failure) {
    String value =
        failure.getResponseHeaders() == null
            ? null
            : failure.getResponseHeaders().getFirst("Retry-After");
    if (value == null) return 1;
    try {
      return Math.max(0, Long.parseLong(value));
    } catch (NumberFormatException invalid) {
      try {
        return Math.max(
            0,
            java.time.Duration.between(
                    Instant.now(),
                    java.time.ZonedDateTime.parse(
                            value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant())
                .getSeconds());
      } catch (Exception unparseable) {
        return 60;
      }
    }
  }

  /** Optional, bounded provider-supplied aspect enrichment. Never guess brand/color/size. */
  public synchronized EbayProduct enrich(EbayProduct product) {
    try {
      throttle();
      String id = URLEncoder.encode(product.itemId(), StandardCharsets.UTF_8);
      var item =
          restClient
              .get()
              .uri(
                  URI.create(
                      normalizeBaseUrl(properties.getApiBaseUrl()) + "/buy/browse/v1/item/" + id))
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentAccessToken())
              .header("X-EBAY-C-MARKETPLACE-ID", properties.getMarketplaceId())
              .retrieve()
              .body(ItemDetails.class);
      if (item == null) return product;
      String brand = aspect(item.localizedAspects(), "Brand"),
          color = aspect(item.localizedAspects(), "Color"),
          size = aspect(item.localizedAspects(), "Size");
      return new EbayProduct(
          product.itemId(),
          product.title(),
          product.shortDescription(),
          product.categoryName(),
          item.price() == null ? product.price() : parsePrice(item.price()),
          item.price() == null ? product.currency() : item.price().currency(),
          product.imageUrl(),
          hasText(item.itemWebUrl()) ? item.itemWebUrl() : product.itemWebUrl(),
          brand,
          color,
          size == null ? null : List.of(size),
          "UNKNOWN",
          product.sellerUsername());
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().value() == 429)
        throw new EbayClientException("eBay detail enrichment rate limited; retry later.");
      return product;
    } catch (RestClientException failure) {
      return product;
    }
  }

  private String aspect(List<Aspect> aspects, String name) {
    if (aspects == null) return null;
    return aspects.stream()
        .filter(a -> a != null && name.equalsIgnoreCase(a.name()) && hasText(a.value()))
        .map(Aspect::value)
        .findFirst()
        .orElse(null);
  }

  private record Aspect(String name, String value) {}

  private record ItemDetails(List<Aspect> localizedAspects, Price price, String itemWebUrl) {}

  public record NotificationPublicKey(String algorithm, String digest, String key) {}

  public synchronized NotificationPublicKey notificationPublicKey(String kid) {
    if (!kid.matches("[A-Za-z0-9_-]{1,256}"))
      throw new IllegalArgumentException("Invalid public key identifier");
    // Notifications use the production key service, never a URL supplied by the caller.
    if (!"https://api.ebay.com".equals(normalizeBaseUrl(properties.getApiBaseUrl())))
      throw new EbayClientException(
          "Production eBay API configuration is required for notifications");
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return restClient
            .get()
            .uri(URI.create("https://api.ebay.com/commerce/notification/v1/public_key/" + kid))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentAccessToken())
            .retrieve()
            .body(NotificationPublicKey.class);
      } catch (RestClientResponseException failure) {
        if (failure.getStatusCode().value() == 401 && attempt == 0) {
          clearCachedToken();
          continue;
        }
        throw new EbayClientException("eBay notification key lookup failed");
      } catch (RestClientException failure) {
        throw new EbayClientException("eBay notification key lookup failed");
      }
    }
    throw new EbayClientException("eBay notification key lookup failed");
  }

  private synchronized String currentAccessToken() {
    if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
      return accessToken;
    }
    requireCredentials();
    TokenResponse response;
    try {
      response =
          restClient
              .post()
              .uri("/identity/v1/oauth2/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
              .body(tokenRequestBody())
              .retrieve()
              .body(TokenResponse.class);
    } catch (RestClientResponseException exception) {
      throw new EbayClientException(
          "eBay token request failed with HTTP " + exception.getStatusCode().value());
    } catch (RestClientException exception) {
      throw new EbayClientException("eBay token request failed");
    }
    if (response == null || !hasText(response.access_token())) {
      throw new EbayClientException("eBay token response did not include an access token");
    }
    accessToken = response.access_token();
    tokenExpiresAt = expiry(response.expires_in());
    return accessToken;
  }

  private void requireCredentials() {
    if (!hasText(properties.getClientId()) || !hasText(properties.getClientSecret())) {
      throw new EbayClientException("eBay credentials are not configured");
    }
  }

  private String basicAuthorization() {
    String credentials = properties.getClientId() + ":" + properties.getClientSecret();
    String encoded =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  private String tokenRequestBody() {
    return "grant_type=client_credentials&scope="
        + URLEncoder.encode(BROWSE_SCOPE, StandardCharsets.UTF_8);
  }

  private URI searchUri(String keyword, int offset) {
    String query =
        "q="
            + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
            + "&limit="
            + properties.getPageSize()
            + "&offset="
            + offset
            + "&fieldgroups=EXTENDED"
            + "&filter="
            + URLEncoder.encode(FIXED_PRICE_FILTER, StandardCharsets.UTF_8);
    return URI.create(
        normalizeBaseUrl(properties.getApiBaseUrl())
            + "/buy/browse/v1/item_summary/search?"
            + query);
  }

  private List<EbayProduct> toProducts(List<ItemSummary> summaries) {
    if (summaries == null || summaries.isEmpty()) {
      return List.of();
    }
    List<EbayProduct> products = new ArrayList<>();
    for (ItemSummary summary : summaries) {
      if (summary == null) {
        continue;
      }
      products.add(
          new EbayProduct(
              summary.itemId(),
              summary.title(),
              summary.shortDescription(),
              leafCategoryName(summary.categories(), summary.leafCategoryIds()),
              parsePrice(summary.price()),
              summary.price() == null ? null : summary.price().currency(),
              summary.image() == null ? null : summary.image().imageUrl(),
              summary.itemWebUrl(),
              null,
              null,
              null,
              "UNKNOWN",
              summary.seller() == null ? null : summary.seller().username()));
    }
    return products;
  }

  private String leafCategoryName(List<Category> categories, List<String> leafCategoryIds) {
    if (categories == null) {
      return null;
    }
    if (leafCategoryIds != null) {
      for (String leafId : leafCategoryIds) {
        if (!hasText(leafId)) {
          continue;
        }
        String wanted = leafId.trim();
        for (Category category : categories) {
          if (category != null
              && hasText(category.categoryId())
              && hasText(category.categoryName())
              && wanted.equals(category.categoryId().trim())) {
            return category.categoryName().trim();
          }
        }
      }
    }
    String name = null;
    for (Category category : categories) {
      if (category != null && hasText(category.categoryName())) {
        name = category.categoryName().trim();
      }
    }
    return name;
  }

  private BigDecimal parsePrice(Price price) {
    if (price == null || !hasText(price.value())) {
      return null;
    }
    try {
      return new BigDecimal(price.value().trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Instant expiry(long expiresInSeconds) {
    long skewSeconds = expiresInSeconds > 60 ? 60 : 0;
    return Instant.now().plusSeconds(Math.max(0, expiresInSeconds - skewSeconds));
  }

  private static String normalizeBaseUrl(String apiBaseUrl) {
    if (!hasText(apiBaseUrl)) {
      return "https://api.ebay.com";
    }
    String trimmed = apiBaseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record TokenResponse(String access_token, long expires_in) {}

  private record SearchResponse(List<ItemSummary> itemSummaries, String next) {}

  private record ItemSummary(
      String itemId,
      String title,
      String shortDescription,
      List<Category> categories,
      List<String> leafCategoryIds,
      Price price,
      Image image,
      String itemWebUrl,
      Seller seller) {}

  private record Seller(String username) {}

  private record Category(String categoryId, String categoryName) {}

  private record Price(String value, String currency) {}

  private record Image(String imageUrl) {}
}
