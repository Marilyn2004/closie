package com.fashionsearch.studio;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Fixed vendor origin; model must be selected by the operator, never by client input. */
@Component
@ConditionalOnProperty(name = "closie.studio.enabled", havingValue = "true")
@ConditionalOnProperty(
    name = "closie.studio.image-provider",
    havingValue = "gemini",
    matchIfMissing = true)
public class GeminiImages implements ImageGenerationProvider {
  private final String key, model;
  private final boolean enabled;
  private final URI base;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final JsonMapper json = JsonMapper.builder().build();

  @org.springframework.beans.factory.annotation.Autowired
  public GeminiImages(
      @Value("${closie.studio.key:}") String key,
      @Value("${closie.studio.image-model:}") String model,
      @Value("${closie.studio.generation-enabled:false}") boolean enabled) {
    this(
        key,
        model,
        enabled,
        URI.create("https://generativelanguage.googleapis.com/v1beta/models/"));
  }

  GeminiImages(String key, String model, boolean enabled, URI base) {
    this.base = base;
    this.key = key;
    this.model = model;
    this.enabled = enabled;
    if (!model.isBlank() && !model.matches("gemini-[a-z0-9.-]{1,90}"))
      throw new IllegalArgumentException("Invalid image model name.");
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.fashionsearch.demo.PublicDemoPolicy demoPolicy;

  public boolean configured() {
    return (demoPolicy == null || !demoPolicy.enabled())
        && enabled
        && !key.isBlank()
        && !model.isBlank();
  }

  public String model() {
    return model;
  }

  public byte[] generate(String prompt, byte[] reference) throws Exception {
    if (!configured()) throw new IllegalStateException("Image provider is not configured.");
    // Verify account-side availability before an expensive request. Do not assume model access.
    var check =
        HttpRequest.newBuilder(base.resolve(model))
            .header("x-goog-api-key", key)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
    var metadata = http.send(check, HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = metadata.body()) {
      if (metadata.statusCode() != 200)
        throw new IOException("Configured image model is unavailable to this account.");
      var data = json.readTree(stream.readNBytes(65536));
      if (!data.path("supportedGenerationMethods").toString().contains("generateContent"))
        throw new IOException("Model lacks generateContent support.");
    }
    var parts = new ArrayList<Object>();
    parts.add(Map.of("text", prompt));
    if (reference != null)
      parts.add(
          Map.of(
              "inlineData",
              Map.of(
                  "mimeType",
                  isPng(reference) ? "image/png" : "image/jpeg",
                  "data",
                  Base64.getEncoder().encodeToString(reference))));
    var body =
        Map.of(
            "contents",
            List.of(Map.of("parts", parts)),
            "generationConfig",
            Map.of("responseModalities", List.of("TEXT", "IMAGE")));
    var request =
        HttpRequest.newBuilder(base.resolve("./" + model + ":generateContent"))
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = response.body()) {
      if (response.statusCode() != 200)
        throw new IOException("Image provider returned status " + response.statusCode());
      byte[] bytes = stream.readNBytes(16 * 1024 * 1024 + 1);
      if (bytes.length > 16 * 1024 * 1024) throw new IOException("Image response too large.");
      var tree = json.readTree(bytes);
      for (var candidate : tree.path("candidates"))
        for (var part : candidate.path("content").path("parts")) {
          var inline = part.path("inlineData");
          if (inline.isMissingNode()) inline = part.path("inline_data");
          if (inline.has("data")) return Base64.getDecoder().decode(inline.path("data").asText());
        }
      throw new IOException("Provider returned no image; the previous version is preserved.");
    }
  }

  private static boolean isPng(byte[] bytes) {
    return bytes.length > 4 && bytes[0] == (byte) 137 && bytes[1] == 80;
  }
}
