package com.fashionsearch.client;

import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.EbayProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(EbayClient.class)
@EnableConfigurationProperties(EbayProperties.class)
class EbayClientTest {

    private static final String CLIENT_ID = "test-client-id";

    private static final String CLIENT_SECRET = "test-client-secret";

    @Autowired
    private EbayClient ebayClient;

    @Autowired
    private EbayProperties ebayProperties;

    @Autowired
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ebayClient.clearCachedToken();
        ebayProperties.setApiBaseUrl("https://api.ebay.com");
        ebayProperties.setClientId(CLIENT_ID);
        ebayProperties.setClientSecret(CLIENT_SECRET);
        ebayProperties.setMarketplaceId("EBAY_US");
        ebayProperties.setPageSize(50);
    }

    @Test
    void requestsBrowseScopeAndFixedPriceSearch() throws Exception {
        String searchJson = new ClassPathResource("ebay/search-page.json").getContentAsString(StandardCharsets.UTF_8);
        server.expect(requestTo(containsString("/identity/v1/oauth2/token")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuthorization()))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().string(allOf(
                        containsString("grant_type=client_credentials"),
                        containsString("scope=https%3A%2F%2Fapi.ebay.com%2Foauth%2Fapi_scope"),
                        not(containsString("buy.item.bulk"))
                )))
                .andRespond(withSuccess(
                        "{\"access_token\":\"test-token\",\"expires_in\":7200,\"token_type\":\"Application Access Token\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(allOf(
                        containsString("/buy/browse/v1/item_summary/search"),
                        containsString("q=leather+jacket"),
                        containsString("limit=50"),
                        containsString("offset=0"),
                        containsString("fieldgroups=EXTENDED"),
                        containsString("filter=buyingOptions%3A%7BFIXED_PRICE%7D")
                )))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header("X-EBAY-C-MARKETPLACE-ID", "EBAY_US"))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));

        EbaySearchPage page = ebayClient.search("leather jacket", 0);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.products()).hasSize(2);
        EbayProduct jacket = page.products().get(0);
        assertThat(jacket.itemId()).isEqualTo("v1|111|0");
        assertThat(jacket.title()).isEqualTo("Black Cropped Leather Jacket");
        assertThat(jacket.shortDescription()).isEqualTo("Fitted minimal leather jacket");
        assertThat(jacket.categoryName()).isEqualTo("Coats, Jackets & Vests");
        assertThat(jacket.price()).isEqualByComparingTo("129.50");
        assertThat(jacket.currency()).isEqualTo("USD");
        assertThat(jacket.imageUrl()).isEqualTo("https://example.com/jacket.jpg");
        assertThat(jacket.itemWebUrl()).isEqualTo("https://www.ebay.com/itm/111");

        EbayProduct cardigan = page.products().get(1);
        assertThat(cardigan.shortDescription()).isNull();
        assertThat(cardigan.imageUrl()).isNull();
        assertThat(cardigan.categoryName()).isEqualTo("Sweaters");
        assertThat(cardigan.currency()).isEqualTo("usd");
        server.verify();
    }

    @Test
    void reusesAccessTokenForTheNextSearch() {
        server.expect(requestTo(containsString("/identity/v1/oauth2/token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"test-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("offset=0")))
                .andRespond(withSuccess("{\"itemSummaries\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("offset=50")))
                .andRespond(withSuccess("{\"itemSummaries\":[],\"next\":null}", MediaType.APPLICATION_JSON));

        EbaySearchPage first = ebayClient.search("dress", 0);
        EbaySearchPage second = ebayClient.search("dress", 50);

        assertThat(first.hasNext()).isFalse();
        assertThat(second.hasNext()).isFalse();
        server.verify();
    }

    @Test
    void reportsTokenHttpFailureWithoutTheClientSecret() {
        server.expect(requestTo(containsString("/identity/v1/oauth2/token")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> ebayClient.search("dress", 0))
                .isInstanceOf(EbayClientException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining(CLIENT_SECRET);
    }

    @Test
    void failsBeforeCallingEbayWhenCredentialsAreMissing() {
        ebayProperties.setClientId("");
        ebayProperties.setClientSecret("");

        assertThatThrownBy(() -> ebayClient.search("dress", 0))
                .isInstanceOf(EbayClientException.class)
                .hasMessageContaining("credentials");
        server.verify();
    }

    private String basicAuthorization() {
        String encoded = Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + encoded;
    }
}
