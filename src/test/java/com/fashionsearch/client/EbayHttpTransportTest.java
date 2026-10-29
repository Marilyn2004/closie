package com.fashionsearch.client;

import static org.assertj.core.api.Assertions.*;

import com.fashionsearch.config.EbayProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class EbayHttpTransportTest {
  @Test
  void actualHttpClientUsesAbsoluteHostAndSingleEncodedQuery() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger tokenRequests = new AtomicInteger(), searchRequests = new AtomicInteger();
    server.createContext(
        "/identity/v1/oauth2/token",
        exchange -> {
          tokenRequests.incrementAndGet();
          byte[] body =
              "{\"access_token\":\"local-test-token\",\"expires_in\":7200}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/buy/browse/v1/item_summary/search",
        exchange -> {
          String query = exchange.getRequestURI().getRawQuery();
          if (query.contains("q=leather+jacket")
              && query.contains("buyingOptions%3A%7BFIXED_PRICE%7D")
              && "EBAY_US".equals(exchange.getRequestHeaders().getFirst("X-EBAY-C-MARKETPLACE-ID")))
            searchRequests.incrementAndGet();
          byte[] body = "{\"itemSummaries\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      EbayProperties properties = new EbayProperties();
      properties.setClientId("test");
      properties.setClientSecret("test");
      properties.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
      var client = new EbayClient(properties, RestClient.builder());
      client.search("leather jacket", 0);
      client.search("leather jacket", 50);
      assertThat(tokenRequests.get()).isEqualTo(1);
      assertThat(searchRequests.get()).isEqualTo(2);
    } finally {
      server.stop(0);
    }
  }
}
