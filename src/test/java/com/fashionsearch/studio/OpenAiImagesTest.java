package com.fashionsearch.studio;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenAiImagesTest {
  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void generationUsesOneExplicitLowCostImageAndReportsUsage() throws Exception {
    var body = new AtomicReference<String>();
    var headers = new AtomicReference<String>();
    var count = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/images/generations",
        ex -> {
          count.incrementAndGet();
          body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          headers.set(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] response = response();
          ex.getResponseHeaders().add("x-request-id", "req-local-fixture");
          ex.sendResponseHeaders(200, response.length);
          ex.getResponseBody().write(response);
          ex.close();
        });
    server.start();
    try {
      var result =
          provider(server).generateWithMetadata("One original white-background cardigan", null);
      assertThat(result.bytes()).isEqualTo("test-image".getBytes());
      var request = json.readTree(body.get());
      assertThat(request.path("model").asText()).isEqualTo(OpenAiImages.MODEL);
      assertThat(request.path("quality").asText()).isEqualTo("low");
      assertThat(request.path("n").asInt()).isEqualTo(1);
      assertThat(request.path("size").asText()).isEqualTo("1024x1024");
      assertThat(request.path("background").asText()).isEqualTo("opaque");
      assertThat(request.has("response_format")).isFalse();
      assertThat(headers.get()).isEqualTo("Bearer fixture-private-key");
      assertThat(count.get()).isEqualTo(1);
      assertThat(result.metadata().get("requestId")).isEqualTo("req-local-fixture");
      assertThat((java.math.BigDecimal) result.metadata().get("estimatedCostUsd"))
          .isEqualByComparingTo("0.006380");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void editsUseMultipartOriginalBytesAndNeverFetchAnExternalImage() throws Exception {
    var body = new AtomicReference<byte[]>();
    var contentType = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/images/edits",
        ex -> {
          body.set(ex.getRequestBody().readAllBytes());
          contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
          byte[] response = response();
          ex.sendResponseHeaders(200, response.length);
          ex.getResponseBody().write(response);
          ex.close();
        });
    server.start();
    byte[] image = {(byte) 137, 80, 78, 71, 0, 1, 2, 3, 4};
    try {
      provider(server).generateWithMetadata("Change only the neckline; preserve stitching", image);
      String request = new String(body.get(), StandardCharsets.ISO_8859_1);
      assertThat(contentType.get()).startsWith("multipart/form-data; boundary=closie-");
      assertThat(request)
          .contains(
              "name=\"image[]\"",
              "image/png",
              "name=\"quality\"\r\n\r\nlow",
              "name=\"size\"\r\n\r\n1024x1024");
      assertThat(request).doesNotContain("fixture-private-key", "input_fidelity");
      assertThat(request).contains(new String(image, StandardCharsets.ISO_8859_1));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failuresAreSanitizedAndNeverAutomaticallyRetried() throws Exception {
    var count = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/images/",
        ex -> {
          count.incrementAndGet();
          byte[] bytes = "fixture-private-key sensitive upstream message".getBytes();
          ex.sendResponseHeaders(429, bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
    try {
      assertThatThrownBy(() -> provider(server).generate("white-background top", null))
          .isInstanceOf(OpenAiImages.ImageApiException.class)
          .hasMessageContaining("quota or rate limit")
          .hasMessageNotContaining("fixture-private-key");
      assertThat(count.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disabledInvalidModelAndOversizedPromptFailBeforeTransport() {
    assertThat(new OpenAiImages("", OpenAiImages.MODEL, true).configured()).isFalse();
    assertThat(new OpenAiImages("fixture", OpenAiImages.MODEL, false).configured()).isFalse();
    assertThatThrownBy(() -> new OpenAiImages("fixture", "gpt-image-1", true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new OpenAiImages("fixture", OpenAiImages.MODEL, true)
                    .generate("a".repeat(4001), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void estimatesIncludeInputTokensAndRejectUnavailableUsage() {
    assertThat(OpenAiImageCost.generationEstimate("x".repeat(4000)))
        .isEqualByComparingTo("0.02588");
    assertThat(OpenAiImageCost.estimateUsage(json.readTree("{}"))).isEmpty();
    assertThat(
            OpenAiImageCost.estimateUsage(
                json.readTree(
                    "{\"input_tokens_details\":{\"text_tokens\":-1,\"image_tokens\":0},\"output_tokens\":196}")))
        .isEmpty();
  }

  @Test
  void malformedKeyIsRejectedWithoutEchoingIt() {
    assertThatThrownBy(() -> new OpenAiImages("fixture-private-key\n", OpenAiImages.MODEL, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("fixture-private-key");
  }

  @Test
  void bodyLimitCancelsTransportBeforeUnboundedAllocation() {
    var subscriber = OpenAiImages.boundedBody(5);
    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    subscriber.onSubscribe(
        new java.util.concurrent.Flow.Subscription() {
          public void request(long n) {}

          public void cancel() {
            cancelled.set(true);
          }
        });
    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap("123456".getBytes())));
    assertThat(cancelled.get()).isTrue();
    assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
  }

  private OpenAiImages provider(HttpServer server) {
    return new OpenAiImages(
        "fixture-private-key",
        OpenAiImages.MODEL,
        true,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/images/"));
  }

  private byte[] response() {
    return "{\"data\":[{\"b64_json\":\"dGVzdC1pbWFnZQ==\"}],\"usage\":{\"input_tokens\":100,\"input_tokens_details\":{\"text_tokens\":100,\"image_tokens\":0},\"output_tokens\":196}}"
        .getBytes(StandardCharsets.UTF_8);
  }
}
