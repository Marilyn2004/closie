package com.fashionsearch.service;

import com.fashionsearch.dto.SearchContext;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UserQueryAi {
  private final String key, model;
  private final boolean enabled;
  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public UserQueryAi(
      @Value("${closie.ai.key:}") String key,
      @Value("${closie.ai.model:}") String model,
      @Value("${closie.ai.enabled:false}") boolean enabled) {
    this.key = key;
    this.model = model;
    this.enabled = enabled;
  }

  public Optional<SearchContext> interpret(String message, SearchContext previous) {
    if (!enabled || key.isBlank() || model.isBlank()) return Optional.empty();
    try {
      Map<String, Object> properties = new LinkedHashMap<>();
      for (String name :
          List.of("keywords", "category", "brand", "color", "currency", "aesthetic", "occasion"))
        properties.put(name, Map.of("type", List.of("string", "null")));
      for (String name : List.of("minPrice", "maxPrice"))
        properties.put(name, Map.of("type", List.of("number", "null")));
      var schema =
          Map.of(
              "type",
              "object",
              "properties",
              properties,
              "required",
              new ArrayList<>(properties.keySet()),
              "additionalProperties",
              false);
      var body =
          Map.of(
              "model",
              model,
              "store",
              false,
              "max_output_tokens",
              600,
              "instructions",
              "Interpret only fashion preferences. Return the FULL merged search context. Keep"
                  + " prior constraints unless user changes them. Never invent brands. Currency"
                  + " USD/EUR/GBP. Null means unspecified. Aesthetic and occasion are soft"
                  + " preferences. Never output products.",
              "input",
              "Previous user preferences: "
                  + json.writeValueAsString(previous)
                  + "\nUser request: "
                  + message,
              "text",
              Map.of(
                  "format",
                  Map.of(
                      "type",
                      "json_schema",
                      "name",
                      "preferences",
                      "strict",
                      true,
                      "schema",
                      schema)));
      var request =
          HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
              .timeout(Duration.ofSeconds(12))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) return Optional.empty();
      var root = json.readTree(response.body());
      if (!"completed".equals(root.path("status").asString())) return Optional.empty();
      for (var output : root.path("output"))
        for (var content : output.path("content"))
          if ("output_text".equals(content.path("type").asString()))
            return Optional.of(
                json.readValue(content.path("text").asString(), SearchContext.class));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
      /* Credential/model/format failures use deterministic interpretation; never log user text or secrets. */
    }
    return Optional.empty();
  }
}
