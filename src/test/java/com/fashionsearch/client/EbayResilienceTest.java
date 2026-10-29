package com.fashionsearch.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fashionsearch.config.EbayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EbayResilienceTest {
  EbayClient client;
  MockRestServiceServer server;

  @BeforeEach
  void setup() {
    var props = new EbayProperties();
    props.setClientId("test");
    props.setClientSecret("test");
    props.setRequestIntervalMillis(0);
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new EbayClient(props, builder);
  }

  void token(String token) {
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/oauth2/token")))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"" + token + "\",\"expires_in\":7200}",
                MediaType.APPLICATION_JSON));
  }

  @Test
  void revokedTokenIsRenewedOnce() {
    token("old");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andExpect(header("Authorization", "Bearer old"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    token("new");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andExpect(header("Authorization", "Bearer new"))
        .andRespond(withSuccess("{\"itemSummaries\":[]}", MediaType.APPLICATION_JSON));
    assertThat(client.search("shirt", 0).products()).isEmpty();
    server.verify();
  }

  @Test
  void transientServerErrorRetriesWithinBound() {
    token("test");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andRespond(withSuccess("{\"itemSummaries\":[]}", MediaType.APPLICATION_JSON));
    client.search("shirt", 0);
    server.verify();
  }

  @Test
  void longRetryAfterDoesNotSleepOrImmediatelyRetry() {
    token("test");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3600"));
    assertThatThrownBy(() -> client.search("shirt", 0))
        .isInstanceOf(EbayClientException.class)
        .hasMessageContaining("rate limit");
    assertThatThrownBy(() -> client.search("shirt", 0)).hasMessageContaining("cooldown");
    server.verify();
  }

  @Test
  void mapsProviderSellerForDeletionWithoutGuessing() {
    token("test");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("item_summary/search")))
        .andRespond(
            withSuccess(
                "{\"itemSummaries\":[{\"itemId\":\"v1|owned|0\",\"title\":\"Cotton"
                    + " Top\",\"price\":{\"value\":\"20\",\"currency\":\"USD\"},\"seller\":{\"username\":\"opaque-seller-id\"}}]}",
                MediaType.APPLICATION_JSON));
    assertThat(client.search("top", 0).products().getFirst().sellerUsername())
        .isEqualTo("opaque-seller-id");
    server.verify();
  }

  @Test
  void enrichmentUsesOnlyProviderSuppliedAspects() {
    token("test");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/item/v1%7C123%7C0")))
        .andRespond(
            withSuccess(
                "{\"localizedAspects\":[{\"name\":\"Brand\",\"value\":\"Provider"
                    + " Brand\"},{\"name\":\"Color\",\"value\":\"Ivory\"},{\"name\":\"Size\",\"value\":\"M\"}],\"price\":{\"value\":\"42.00\",\"currency\":\"USD\"}}",
                MediaType.APPLICATION_JSON));
    var p =
        client.enrich(
            new com.fashionsearch.dto.EbayProduct(
                "v1|123|0",
                "Shirt",
                null,
                "Tops",
                java.math.BigDecimal.TEN,
                "USD",
                "https://example.com/i",
                "https://example.com/p"));
    assertThat(p.brand()).isEqualTo("Provider Brand");
    assertThat(p.color()).isEqualTo("Ivory");
    assertThat(p.sizes()).containsExactly("M");
    assertThat(p.availability()).isEqualTo("UNKNOWN");
    server.verify();
  }
}
