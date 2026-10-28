package com.fashionsearch.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fashionsearch.config.EbayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EbayNotificationTransportTest {
  @Test
  void fetchesProductionKeyAndRenewsRevokedTokenOnce() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var properties = new EbayProperties();
    properties.setClientId("test");
    properties.setClientSecret("test-secret");
    var client = new EbayClient(properties, builder);
    server
        .expect(requestTo("https://api.ebay.com/identity/v1/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"first-token\",\"expires_in\":7200}",
                MediaType.APPLICATION_JSON));
    String keyUrl = "https://api.ebay.com/commerce/notification/v1/public_key/key-id";
    server
        .expect(requestTo(keyUrl))
        .andExpect(header("Authorization", "Bearer first-token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    server
        .expect(requestTo("https://api.ebay.com/identity/v1/oauth2/token"))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"new-token\",\"expires_in\":7200}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(keyUrl))
        .andExpect(header("Authorization", "Bearer new-token"))
        .andRespond(
            withSuccess(
                "{\"algorithm\":\"ECDSA\",\"digest\":\"SHA1\",\"key\":\"public-key\"}",
                MediaType.APPLICATION_JSON));
    assertThat(client.notificationPublicKey("key-id"))
        .isEqualTo(new EbayClient.NotificationPublicKey("ECDSA", "SHA1", "public-key"));
    server.verify();
  }

  @Test
  void rejectsUntrustedPathsAndNonProductionHostsBeforeAnyRequest() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var properties = new EbayProperties();
    var client = new EbayClient(properties, builder);
    assertThatThrownBy(() -> client.notificationPublicKey("../untrusted"))
        .isInstanceOf(IllegalArgumentException.class);
    properties.setApiBaseUrl("http://127.0.0.1:9999");
    assertThatThrownBy(() -> client.notificationPublicKey("key-id"))
        .isInstanceOf(EbayClientException.class)
        .hasMessageContaining("Production");
    server.verify();
  }
}
