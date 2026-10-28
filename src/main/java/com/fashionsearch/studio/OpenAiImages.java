package com.fashionsearch.studio;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Direct Images API: one explicitly priced output, without a second text-model call. */
@Component
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
@ConditionalOnProperty(name = "closie.studio.image-provider", havingValue = "openai")
public class OpenAiImages implements ImageGenerationProvider {
  public static final String MODEL = "gpt-image-2.5-flare-2026-09-08";
  private final String key, model;
  private final boolean enabled;
  private final URI base;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final JsonMapper json = JsonMapper.builder().build();

  @Autowired
  public OpenAiImages(
      @Value("${closie.studio.openai.key:}") String key,
      @Value("${closie.studio.openai.model:gpt-image-2.5-flare-2026-09-08}") String model,
      @Value("${closie.studio.generation-enabled:false}") boolean enabled) {
    this(key, model, enabled, URI.create("https://api.openai.com/v1/images/"));
  }

  OpenAiImages(String key, String model, boolean enabled, URI base) {
    if (!Set.of(MODEL, "gpt-image-2.5-flare").contains(model))
      throw new IllegalArgumentException(
          "Choose the supported budgeted OpenAI image model. Revalidate pricing before changing"
              + " models.");
    if (!key.isBlank() && (key.length() > 512 || key.chars().anyMatch(c -> c < 33 || c > 126)))
      throw new IllegalArgumentException(
          "The backend API key contains invalid characters. Re-enter it privately; never include it"
              + " in error reports.");
    this.key = key;
    this.model = model;
    this.enabled = enabled;
    this.base = base;
  }

  public String name() {
    return "OPENAI";
  }

  public String model() {
    return model;
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.fashionsearch.demo.PublicDemoPolicy demoPolicy;

  public boolean configured() {
    return (demoPolicy == null || !demoPolicy.enabled()) && enabled && !key.isBlank();
  }

  public String settingsKey() {
    return name() + ":" + model + ":low:1024x1024:jpeg:opaque:v1";
  }

  public byte[] generate(String prompt, byte[] reference) throws Exception {
    return generateWithMetadata(prompt, reference).bytes();
  }

  public GeneratedImage generateWithMetadata(String prompt, byte[] reference) throws Exception {
    if (!configured()) throw new IllegalStateException("OpenAI images are not configured.");
    if (prompt == null || prompt.isBlank() || prompt.getBytes(StandardCharsets.UTF_8).length > 4000)
      throw new IllegalArgumentException(
          "Image brief exceeds the budgeted 4000-byte prompt limit.");
    if (reference != null && (reference.length == 0 || reference.length > 8 * 1024 * 1024))
      throw new IllegalArgumentException("Invalid reference image.");
    var fields = new LinkedHashMap<String, Object>();
    fields.put("model", model);
    fields.put("prompt", prompt);
    fields.put("n", 1);
    fields.put("quality", "low");
    fields.put("size", "1024x1024");
    fields.put("output_format", "jpeg");
    fields.put("background", "opaque");
    HttpRequest.BodyPublisher publisher;
    String contentType;
    if (reference == null) {
      publisher = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(fields));
      contentType = "application/json";
    } else {
      // Byte-array parts avoid file-name injection and do not upload files to the Files API.
      String boundary = "closie-" + UUID.randomUUID();
      var out = new ByteArrayOutputStream();
      for (var entry : fields.entrySet())
        out.write(
            ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\""
                    + entry.getKey()
                    + "\"\r\n\r\n"
                    + entry.getValue()
                    + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
      String mime =
          reference.length > 4 && reference[0] == (byte) 137 && reference[1] == 80
              ? "image/png"
              : "image/jpeg";
      out.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"reference."
                  + (mime.equals("image/png") ? "png" : "jpg")
                  + "\"\r\nContent-Type: "
                  + mime
                  + "\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      out.write(reference);
      out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      publisher = HttpRequest.BodyPublishers.ofByteArray(out.toByteArray());
      contentType = "multipart/form-data; boundary=" + boundary;
    }
    var request =
        HttpRequest.newBuilder(base.resolve(reference == null ? "generations" : "edits"))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", contentType)
            .timeout(Duration.ofSeconds(130))
            .POST(publisher)
            .build();
    var pending = http.sendAsync(request, ignored -> boundedBody(16 * 1024 * 1024));
    HttpResponse<byte[]> response;
    try {
      response = pending.get(135, java.util.concurrent.TimeUnit.SECONDS);
    } catch (java.util.concurrent.TimeoutException timeout) {
      pending.cancel(true);
      throw new java.net.http.HttpTimeoutException(
          "Image request timed out. Billing status is unknown; no automatic retry was made.");
    } catch (InterruptedException interrupted) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw interrupted;
    } catch (java.util.concurrent.ExecutionException failure) {
      throw new IOException("Image transport failed. No automatic retry was made.");
    }
    String requestId =
        response
            .headers()
            .firstValue("x-request-id")
            .filter(s -> s.matches("[a-zA-Z0-9_-]{1,160}"))
            .orElse("");
    {
      if (response.statusCode() != 200)
        throw new ImageApiException(response.statusCode(), requestId);
      byte[] bytes = response.body();
      if (bytes.length > 16 * 1024 * 1024)
        throw new IOException("Image response exceeds the size limit.");
      JsonNode data = json.readTree(bytes);
      if (data.path("data").size() != 1 || !data.path("data").path(0).hasNonNull("b64_json"))
        throw new IOException("Provider did not return exactly one image.");
      byte[] image =
          Base64.getDecoder().decode(data.path("data").path(0).path("b64_json").asText());
      var metadata = new LinkedHashMap<String, Object>();
      metadata.put("provider", name());
      metadata.put("model", model);
      metadata.put("qualitySetting", "low");
      metadata.put("size", "1024x1024");
      metadata.put("requestId", requestId);
      metadata.put("usage", data.path("usage"));
      OpenAiImageCost.estimateUsage(data.path("usage"))
          .ifPresent(cost -> metadata.put("estimatedCostUsd", cost));
      metadata.put(
          "costStatus",
          metadata.containsKey("estimatedCostUsd")
              ? "ESTIMATED_FROM_REPORTED_TOKENS"
              : "USAGE_UNAVAILABLE_RESERVATION_RETAINED");
      return new GeneratedImage(image, Map.copyOf(metadata));
    }
  }

  static HttpResponse.BodySubscriber<byte[]> boundedBody(int maximum) {
    return new HttpResponse.BodySubscriber<>() {
      final java.util.concurrent.CompletableFuture<byte[]> result =
          new java.util.concurrent.CompletableFuture<>();
      final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      java.util.concurrent.Flow.Subscription subscription;

      public java.util.concurrent.CompletionStage<byte[]> getBody() {
        return result;
      }

      public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
        subscription = value;
        value.request(1);
      }

      public void onNext(List<java.nio.ByteBuffer> chunks) {
        for (var chunk : chunks) {
          if ((long) buffer.size() + chunk.remaining() > maximum) {
            subscription.cancel();
            result.completeExceptionally(new IOException("Image response exceeds the size limit."));
            return;
          }
          byte[] part = new byte[chunk.remaining()];
          chunk.get(part);
          buffer.writeBytes(part);
        }
        subscription.request(1);
      }

      public void onError(Throwable error) {
        result.completeExceptionally(error);
      }

      public void onComplete() {
        result.complete(buffer.toByteArray());
      }
    };
  }

  public static final class ImageApiException extends IOException {
    final int status;
    final String requestId;

    ImageApiException(int status, String requestId) {
      super(
          switch (status) {
            case 401 -> "OpenAI authentication failed. Check the backend API key.";
            case 403 ->
                "This OpenAI project cannot access the image model. Check model permissions and"
                    + " organization verification.";
            case 429 -> "OpenAI quota or rate limit reached. No automatic retry was made.";
            default ->
                "OpenAI image request failed (HTTP " + status + "). No automatic retry was made.";
          });
      this.status = status;
      this.requestId = requestId;
    }
  }
}
