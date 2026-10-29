package com.fashionsearch.service;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LlmGateway {
  private final String key, model;
  private final boolean enabled;
  private final URI endpoint;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final JsonMapper json = JsonMapper.builder().build();
  private final Semaphore capacity = new Semaphore(2);

  @Autowired
  public LlmGateway(
      @Value("${closie.ai.key:}") String key,
      @Value("${closie.ai.model:}") String model,
      @Value("${closie.ai.enabled:false}") boolean enabled) {
    this(key, model, enabled, URI.create("https://api.openai.com/v1/responses"));
  }

  LlmGateway(String key, String model, boolean enabled, URI endpoint) {
    this.key = key;
    this.model = model;
    this.enabled = enabled;
    this.endpoint = endpoint;
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.fashionsearch.demo.PublicDemoPolicy demoPolicy;

  public boolean configured() {
    return (demoPolicy == null || !demoPolicy.enabled())
        && enabled
        && !key.isBlank()
        && !model.isBlank();
  }

  public Optional<JsonNode> request(
      String name, Map<String, Object> schema, String instructions, Object input) {
    if (!configured() || !capacity.tryAcquire()) return Optional.empty();
    try {
      String payload = json.writeValueAsString(input);
      if (payload.length() > 24000) return Optional.empty();
      var body =
          Map.of(
              "model",
              model,
              "store",
              false,
              "max_output_tokens",
              1600,
              "instructions",
              instructions,
              "input",
              payload,
              "text",
              Map.of(
                  "format",
                  Map.of("type", "json_schema", "name", name, "strict", true, "schema", schema)));
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(12))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] bytes = stream.readNBytes(128001);
        if (response.statusCode() != 200 || bytes.length > 128000) return Optional.empty();
        var root = json.readTree(bytes);
        if (!"completed".equals(root.path("status").asString())) return Optional.empty();
        for (var output : root.path("output"))
          for (var part : output.path("content"))
            if ("output_text".equals(part.path("type").asString()))
              return Optional.of(json.readTree(part.path("text").asString()));
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception failure) {
      org.slf4j.LoggerFactory.getLogger(LlmGateway.class)
          .debug("Model request unavailable: {}", failure.getClass().getSimpleName());
    } finally {
      capacity.release();
    }
    return Optional.empty();
  }

  public static Map<String, Object> objectSchema(Map<String, Object> properties) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        new ArrayList<>(properties.keySet()),
        "additionalProperties",
        false);
  }
}
