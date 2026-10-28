package com.fashionsearch.notification;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(EbayNotificationConfig.PATH)
@ConditionalOnProperty(name = "closie.ebay-notifications.enabled", havingValue = "true")
public class EbayNotificationController {
  private final EbayNotificationConfig config;
  private final EbayNotificationVerifier verifier;
  private final EbayDeletionProcessor processor;
  private final JsonMapper json =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  public EbayNotificationController(
      EbayNotificationConfig config,
      EbayNotificationVerifier verifier,
      EbayDeletionProcessor processor) {
    this.config = config;
    this.verifier = verifier;
    this.processor = processor;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> challenge(@RequestParam("challenge_code") String challenge) {
    if (challenge.isBlank() || challenge.length() > 1024) throw badRequest();
    return Map.of(
        "challengeResponse",
        EbayDeletionProcessor.hash(challenge + config.verificationToken() + config.endpoint()));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> receive(
      HttpServletRequest request,
      @RequestHeader(value = "X-EBAY-SIGNATURE", required = false) String signature)
      throws IOException {
    if (request.getContentLengthLong() > 16384)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    byte[] body = request.getInputStream().readNBytes(16385);
    if (body.length > 16384) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    tools.jackson.databind.JsonNode payload;
    try {
      payload = json.readTree(body);
    } catch (RuntimeException ex) {
      throw badRequest();
    }
    if (payload == null || !payload.isObject()) throw badRequest();
    if (!verifier.verify(body, json.writeValueAsBytes(payload), signature))
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Invalid eBay signature");
    var metadata = payload.path("metadata");
    var event = payload.path("notification");
    String id = event.path("notificationId").asText("");
    if (!"MARKETPLACE_ACCOUNT_DELETION".equals(metadata.path("topic").asText())
        || !"1.0".equals(metadata.path("schemaVersion").asText())
        || id.isBlank()
        || id.length() > 256
        || !event.path("data").isObject()
        || (event.path("data").path("userId").asText("").isBlank()
            && event.path("data").path("username").asText("").isBlank()
            && event.path("data").path("eiasToken").asText("").isBlank())) throw badRequest();
    try {
      // Do not reject old events: eBay legitimately retries notifications.
      Instant.parse(event.path("eventDate").asText(""));
      Instant.parse(event.path("publishDate").asText(""));
    } catch (RuntimeException ex) {
      throw badRequest();
    }
    processor.process(
        id,
        event.path("data").path("username").asText(""),
        java.util.List.of(
            event.path("data").path("userId").asText(""),
            event.path("data").path("eiasToken").asText("")));
    return ResponseEntity.noContent().build();
  }

  private static ResponseStatusException badRequest() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid eBay notification request");
  }
}
