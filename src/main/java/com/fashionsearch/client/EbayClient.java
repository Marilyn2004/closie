package com.fashionsearch.client;

import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.EbayProduct;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
        this.restClient = restClientBuilder.baseUrl(normalizeBaseUrl(properties.getApiBaseUrl())).build();
    }

    void clearCachedToken() {
        accessToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    public EbaySearchPage search(String keyword, int offset) {
        String token = currentAccessToken();
        SearchResponse response;
        try {
            response = restClient.get()
                    .uri(searchUri(keyword, offset))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-EBAY-C-MARKETPLACE-ID", properties.getMarketplaceId())
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientResponseException exception) {
            throw new EbayClientException("eBay search failed with HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new EbayClientException("eBay search failed");
        }
        if (response == null) {
            throw new EbayClientException("eBay search response was empty");
        }
        return new EbaySearchPage(toProducts(response.itemSummaries()), hasText(response.next()));
    }

    private String currentAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        requireCredentials();
        TokenResponse response;
        try {
            response = restClient.post()
                    .uri("/identity/v1/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                    .body(tokenRequestBody())
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw new EbayClientException("eBay token request failed with HTTP " + exception.getStatusCode().value());
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
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String tokenRequestBody() {
        return "grant_type=client_credentials&scope=" + URLEncoder.encode(BROWSE_SCOPE, StandardCharsets.UTF_8);
    }

    private URI searchUri(String keyword, int offset) {
        String query = "q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&limit=" + properties.getPageSize()
                + "&offset=" + offset
                + "&fieldgroups=EXTENDED"
                + "&filter=" + URLEncoder.encode(FIXED_PRICE_FILTER, StandardCharsets.UTF_8);
        return URI.create("/buy/browse/v1/item_summary/search?" + query);
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
            products.add(new EbayProduct(
                    summary.itemId(),
                    summary.title(),
                    summary.shortDescription(),
                    leafCategoryName(summary.categories(), summary.leafCategoryIds()),
                    parsePrice(summary.price()),
                    summary.price() == null ? null : summary.price().currency(),
                    summary.image() == null ? null : summary.image().imageUrl(),
                    summary.itemWebUrl()
            ));
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

    private record TokenResponse(String access_token, long expires_in) {
    }

    private record SearchResponse(List<ItemSummary> itemSummaries, String next) {
    }

    private record ItemSummary(
            String itemId,
            String title,
            String shortDescription,
            List<Category> categories,
            List<String> leafCategoryIds,
            Price price,
            Image image,
            String itemWebUrl
    ) {
    }

    private record Category(String categoryId, String categoryName) {
    }

    private record Price(String value, String currency) {
    }

    private record Image(String imageUrl) {
    }
}
