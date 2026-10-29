package com.fashionsearch.studio;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiImagesTest {
  @Test
  void transportChecksModelBeforeGenerationAndSendsOriginalImage() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var count = new AtomicInteger();
    var valid = new AtomicInteger();
    server.createContext(
        "/models/gemini-local-image",
        ex -> {
          count.incrementAndGet();
          String response;
          if (ex.getRequestURI().getPath().endsWith(":generateContent")) {
            String request = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request.contains("responseModalities")
                && request.contains("inlineData")
                && request.contains("image/png")
                && "test-private-key".equals(ex.getRequestHeaders().getFirst("x-goog-api-key")))
              valid.incrementAndGet();
            response =
                "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"aW1hZ2U=\"}}]}}]}";
          } else response = "{\"supportedGenerationMethods\":[\"generateContent\"]}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
    try {
      var provider =
          new GeminiImages(
              "test-private-key",
              "gemini-local-image",
              true,
              URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models/"));
      assertThat(provider.generate("strict garment brief", new byte[] {(byte) 137, 80, 78, 71, 1}))
          .isEqualTo("image".getBytes());
      assertThat(count.get()).isEqualTo(2);
      assertThat(valid.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void missingModelAccessStopsBeforePaidRequest() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var count = new AtomicInteger();
    server.createContext(
        "/models/",
        ex -> {
          count.incrementAndGet();
          ex.sendResponseHeaders(403, -1);
          ex.close();
        });
    server.start();
    try {
      var provider =
          new GeminiImages(
              "test",
              "gemini-local-image",
              true,
              URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models/"));
      assertThatThrownBy(() -> provider.generate("brief", null))
          .isInstanceOf(java.io.IOException.class)
          .hasMessageNotContaining("test");
      assertThat(count.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disabledAndMalformedModelsFailClosed() {
    assertThat(new GeminiImages("secret", "gemini-local-image", false).configured()).isFalse();
    assertThatThrownBy(() -> new GeminiImages("secret", "../../evil", true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
