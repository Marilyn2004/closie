package com.fashionsearch.service;

import com.fashionsearch.dto.SearchContext;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UserQueryAi {
  private final LlmGateway gateway;
  private final JsonMapper json = JsonMapper.builder().build();

  @Autowired
  public UserQueryAi(LlmGateway gateway) {
    this.gateway = gateway;
  }

  public UserQueryAi(String key, String model, boolean enabled) {
    this(new LlmGateway(key, model, enabled));
  }

  public Optional<SearchContext> interpret(String message, SearchContext previous) {
    Map<String, Object> properties = new LinkedHashMap<>();
    for (String name :
        List.of(
            "keywords",
            "category",
            "brand",
            "color",
            "currency",
            "aesthetic",
            "occasion",
            "vibe",
            "mood")) properties.put(name, Map.of("type", List.of("string", "null")));
    for (String name : List.of("minPrice", "maxPrice"))
      properties.put(name, Map.of("type", List.of("number", "null")));
    return gateway
        .request(
            "styling_preferences",
            LlmGateway.objectSchema(properties),
            "Interpret the user's fashion request and return the full merged preferences. Retain"
                + " previous constraints unless explicitly changed. Brand is a HARD filter only"
                + " when explicitly buying that brand; 'Aritzia vibe' belongs in vibe, not brand."
                + " Occasion, aesthetic, mood and vibe are soft preferences. Keywords describe"
                + " garments, not filler. Return no products. Currency must be USD/EUR/GBP. Null"
                + " means unspecified.",
            Map.of("previous", previous, "request", message))
        .flatMap(
            node -> {
              try {
                return Optional.of(json.treeToValue(node, SearchContext.class));
              } catch (Exception invalid) {
                return Optional.empty();
              }
            });
  }
}
