package com.fashionsearch.studio;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Standard rates checked 2026-10-28. Estimates are not billing-account statements. */
public final class OpenAiImageCost {
  private OpenAiImageCost() {}

  public static final BigDecimal RESERVATION = new BigDecimal("0.25");
  public static final BigDecimal PROJECT_LIMIT = new BigDecimal("3.00");

  public static BigDecimal generationEstimate(String prompt) {
    // UTF-8 bytes conservatively bound prompt tokens; 196 output tokens from the explicit low/1024
    // calculator.
    int bytes = prompt.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > 4000) throw new IllegalArgumentException("Image prompt exceeds the priced limit.");
    return new BigDecimal(bytes)
        .multiply(new BigDecimal("0.000005"))
        .add(new BigDecimal("0.00588"));
  }

  public static Optional<BigDecimal> estimateUsage(JsonNode usage) {
    var details = usage.path("input_tokens_details");
    if (!details.has("text_tokens") || !details.has("image_tokens") || !usage.has("output_tokens"))
      return Optional.empty();
    for (var node :
        java.util.List.of(
            details.path("text_tokens"), details.path("image_tokens"), usage.path("output_tokens")))
      if (!node.isIntegralNumber() || node.asLong(-1) < 0 || node.asLong() > 10000000)
        return Optional.empty();
    return Optional.of(
        BigDecimal.valueOf(details.path("text_tokens").asLong())
            .multiply(new BigDecimal("0.000005"))
            .add(
                BigDecimal.valueOf(details.path("image_tokens").asLong())
                    .multiply(new BigDecimal("0.000008")))
            .add(
                BigDecimal.valueOf(usage.path("output_tokens").asLong())
                    .multiply(new BigDecimal("0.000030"))));
  }
}
