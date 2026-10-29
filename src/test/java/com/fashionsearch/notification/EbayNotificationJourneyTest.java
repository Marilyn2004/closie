package com.fashionsearch.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.client.*;
import com.fashionsearch.dto.EbayProduct;
import com.fashionsearch.service.ProductIngestionService;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.ebay-notifications.enabled=true",
      "closie.ebay-notifications.endpoint=https://closie.example.com/api/ebay/account-deletion",
      "closie.ebay-notifications.verification-token=01234567890123456789012345678901"
    })
@Testcontainers
class EbayNotificationJourneyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProductIngestionService ingestion;
  @MockitoBean EbayClient ebay;
  private static final KeyPair PAIR;

  static {
    try {
      var gen = KeyPairGenerator.getInstance("EC");
      gen.initialize(256);
      PAIR = gen.generateKeyPair();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM ebay_deletion_receipts");
    jdbc.update("UPDATE ebay_catalog_control SET imports_paused=FALSE WHERE id=1");
    for (String source : List.of("ebay", "closie-demo", "another-provider"))
      jdbc.update(
          "INSERT INTO products(external_id,title,price,currency,source,created_at,updated_at)"
              + " VALUES ('test','Test',10,'USD',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          source);
    when(ebay.notificationPublicKey(anyString()))
        .thenReturn(
            new EbayClient.NotificationPublicKey(
                "ECDSA",
                "SHA1",
                Base64.getEncoder().encodeToString(PAIR.getPublic().getEncoded())));
    when(ebay.search(anyString(), anyInt()))
        .thenReturn(
            new EbaySearchPage(
                List.of(
                    new EbayProduct(
                        "new", "New top", null, "Tops", BigDecimal.TEN, "USD", null, null)),
                false));
  }

  private String body(String id) {
    return "{\"metadata\":{\"topic\":\"MARKETPLACE_ACCOUNT_DELETION\",\"schemaVersion\":\"1.0\"},\"notification\":{\"notificationId\":\""
        + id
        + "\",\"eventDate\":\"2026-01-01T00:00:00Z\",\"publishDate\":\"2026-01-01T00:00:00Z\",\"data\":{\"userId\":\"private-account-id\"}}}";
  }

  private String sign(String body) throws Exception {
    var sig = Signature.getInstance("SHA1withECDSA");
    sig.initSign(PAIR.getPrivate());
    sig.update(body.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder()
        .encodeToString(
            ("{\"kid\":\"journey-key\",\"signature\":\""
                    + Base64.getEncoder().encodeToString(sig.sign())
                    + "\"}")
                .getBytes(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> post(String body, String signature) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + EbayNotificationConfig.PATH))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (signature != null) request.header("X-EBAY-SIGNATURE", signature);
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void challengeUsesExactConfiguredEndpoint() throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + EbayNotificationConfig.PATH
                        + "?challenge_code=test123"))
            .GET()
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    String expected = "70fb6e40c27ee0d90198c9f686a94bd7da4d0fa3b49740cebc53e2f1fc46456e";
    assertThat(response.body()).contains("\"challengeResponse\":\"" + expected + "\"");
    assertThat(response.headers().firstValue("content-type").orElse(""))
        .contains("application/json");
  }

  @Test
  void signedDeletionPurgesOnlyEbayAndPausesImports() throws Exception {
    String body = body("event-one");
    assertThat(post(body, sign(body)).statusCode()).isEqualTo(204);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM products WHERE source='ebay'", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject("SELECT imports_paused FROM ebay_catalog_control", Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT notification_hash FROM ebay_deletion_receipts", String.class))
        .isEqualTo(EbayDeletionProcessor.hash("event-one"))
        .doesNotContain("private-account-id");
    assertThatThrownBy(() -> ingestion.importFashionProducts())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("paused");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM products WHERE source='ebay'", Integer.class))
        .isZero();
  }

  @Test
  void deletionDuringAnInFlightFetchCannotRestoreProducts() throws Exception {
    var fetching = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(ebay.search(anyString(), anyInt()))
        .thenAnswer(
            call -> {
              fetching.countDown();
              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("test timeout");
              return new EbaySearchPage(
                  List.of(
                      new EbayProduct(
                          "in-flight", "New top", null, "Tops", BigDecimal.TEN, "USD", null, null)),
                  false);
            });
    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var pending = executor.submit(() -> ingestion.importFashionProducts());
      assertThat(fetching.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      String body = body("during-fetch");
      assertThat(post(body, sign(body)).statusCode()).isEqualTo(204);
      release.countDown();
      assertThatThrownBy(() -> pending.get(5, java.util.concurrent.TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM products WHERE source='ebay'", Integer.class))
          .isZero();
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void retriesAreIdempotent() throws Exception {
    String body = body("same-event");
    String signature = sign(body);
    assertThat(post(body, signature).statusCode()).isEqualTo(204);
    assertThat(post(body, signature).statusCode()).isEqualTo(204);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT purged_products FROM ebay_deletion_receipts", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void unsignedAndTamperedNotificationsCannotMutateCatalog() throws Exception {
    String original = body("authentic"), tampered = body("tampered");
    assertThat(post(original, null).statusCode()).isEqualTo(412);
    assertThat(post(tampered, sign(original)).statusCode()).isEqualTo(412);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Integer.class))
        .isZero();
  }

  @Test
  void wrongTopicMalformedAndOversizedBodiesCannotMutateCatalog() throws Exception {
    String wrong = body("wrong-topic").replace("MARKETPLACE_ACCOUNT_DELETION", "OTHER_TOPIC");
    assertThat(post(wrong, sign(wrong)).statusCode()).isEqualTo(400);
    assertThat(post("broken", null).statusCode()).isEqualTo(400);
    assertThat(post("x".repeat(16385), null).statusCode()).isEqualTo(413);
    String duplicate =
        body("duplicate")
            .replace(
                "\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"1.0\",\"schemaVersion\":\"2.0\"");
    assertThat(post(duplicate, sign(duplicate)).statusCode()).isEqualTo(400);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(3);
  }

  @Test
  void processingFailureRollsBackReceiptAndPause() {
    jdbc.execute(
        "ALTER TABLE ebay_deletion_receipts ADD CONSTRAINT test_no_purge CHECK"
            + " (purged_products=0)");
    try {
      var processor =
          new org.springframework.transaction.support.TransactionTemplate(
              new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
      assertThatThrownBy(
              () ->
                  processor.execute(
                      status -> {
                        new EbayDeletionProcessor(jdbc).process("rollback");
                        return null;
                      }))
          .isInstanceOf(RuntimeException.class);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(3);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject("SELECT imports_paused FROM ebay_catalog_control", Boolean.class))
          .isFalse();
    } finally {
      jdbc.execute("ALTER TABLE ebay_deletion_receipts DROP CONSTRAINT test_no_purge");
    }
  }
}
