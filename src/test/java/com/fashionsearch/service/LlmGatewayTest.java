package com.fashionsearch.service;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {
  @Test
  void sendsStructuredBoundedRequestAndParsesCompletedOutput() throws Exception {
    AtomicReference<String> sent = new AtomicReference<>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/responses",
        exchange -> {
          sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body =
              "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"answer\\\":\\\"ok\\\"}\"}]}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var gateway =
          new LlmGateway(
              "test-not-a-secret",
              "test-model",
              true,
              URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"));
      var result =
          gateway.request(
              "test",
              LlmGateway.objectSchema(Map.of("answer", Map.of("type", "string"))),
              "Interpret preferences",
              Map.of("request", "feminine coffee date"));
      assertThat(result.orElseThrow().path("answer").asString()).isEqualTo("ok");
      assertThat(sent.get())
          .contains("\"store\":false", "\"strict\":true", "json_schema", "feminine coffee date")
          .doesNotContain("test-not-a-secret");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void httpFailureMalformedAndIncompleteOutputsFallBack() throws Exception {
    for (String response :
        List.of(
            "{bad json",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}")) {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      try {
        var gateway =
            new LlmGateway(
                "test",
                "test",
                true,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        assertThat(gateway.request("test", Map.of(), "test", Map.of())).isEmpty();
      } finally {
        server.stop(0);
      }
    }
  }

  @Test
  void missingCredentialsSkipHttp() {
    assertThat(new LlmGateway("", "", true).request("test", Map.of(), "test", Map.of())).isEmpty();
  }
}
