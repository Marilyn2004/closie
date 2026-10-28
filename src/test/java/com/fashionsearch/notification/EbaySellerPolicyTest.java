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
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "closie.ebay-accounts.enabled=true",
      "closie.ebay-accounts.hash-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "import.token=test-admin-token",
      "closie.providers.ebay.display-enabled=true"
    })
@Testcontainers
class EbaySellerPolicyTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired JdbcTemplate jdbc;
  @Autowired EbayAccountPolicy policy;
  @Autowired EbayDeletionProcessor deletion;
  @Autowired ProductIngestionService ingestion;
  @Autowired TransactionTemplate tx;
  @MockitoBean EbayClient ebay;
  @LocalServerPort int port;

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM ebay_deletion_receipts");
    jdbc.update("DELETE FROM ebay_deleted_accounts");
    jdbc.update(
        "UPDATE ebay_catalog_control SET"
            + " imports_paused=FALSE,pause_reason=NULL,hash_key_fingerprint=NULL WHERE id=1");
    when(ebay.search(anyString(), anyInt())).thenReturn(new EbaySearchPage(List.of(), false));
  }

  private void product(String id, String source, String owner) {
    jdbc.update(
        "INSERT INTO"
            + " products(external_id,title,source,price,currency,created_at,updated_at,ebay_owner_hash)"
            + " VALUES (?, 'Top',?,10,'USD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
        id,
        source,
        owner == null ? null : policy.ownerHash(owner));
  }

  private EbayProduct listing(String id, String seller) {
    return new EbayProduct(
        id,
        "Cotton Top",
        null,
        "Tops",
        BigDecimal.TEN,
        "USD",
        "https://example.com/image",
        "https://example.com/item",
        null,
        null,
        null,
        "UNKNOWN",
        seller);
  }

  @Test
  void deletesOnlyMatchingSellerAndSuppressesReimport() {
    product("deleted", "ebay", "seller-a");
    product("kept", "ebay", "seller-b");
    product("sample", "closie-demo", null);
    deletion.process("notice-a", "seller-a", List.of("immutable-user-a"));
    assertThat(jdbc.queryForList("SELECT external_id FROM products", String.class))
        .containsExactlyInAnyOrder("kept", "sample");
    assertThat(
            jdbc.queryForObject("SELECT imports_paused FROM ebay_catalog_control", Boolean.class))
        .isFalse();
    when(ebay.search(anyString(), anyInt()))
        .thenReturn(
            new EbaySearchPage(
                List.of(listing("deleted", "seller-a"), listing("new", "seller-c")), false));
    var result = ingestion.importFashionProducts();
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.skipped()).isGreaterThan(0);
    assertThat(jdbc.queryForList("SELECT external_id FROM products", String.class))
        .doesNotContain("deleted")
        .contains("new");
    assertThat(
            jdbc.queryForObject(
                "SELECT owner_hash FROM ebay_deleted_accounts LIMIT 1", String.class))
        .hasSize(64)
        .doesNotContain("seller-a");
    assertThat(
            jdbc.queryForObject("SELECT handling_mode FROM ebay_deletion_receipts", String.class))
        .isEqualTo("SELLER_SCOPED");
  }

  @Test
  void publicProductResponseNeverExposesSellerIdentifiers() throws Exception {
    when(ebay.search(anyString(), anyInt()))
        .thenReturn(new EbaySearchPage(List.of(listing("public", "private-seller")), false));
    ingestion.importFashionProducts();
    Long id = jdbc.queryForObject("SELECT id FROM products WHERE external_id='public'", Long.class);
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/products/" + id))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .doesNotContain(
            "private-seller",
            policy.ownerHash("private-seller"),
            "ebayOwnerHash",
            "ownerIdentifier");
  }

  @Test
  void unrelatedAndRepeatedEventsRetainOtherSellers() {
    product("kept", "ebay", "seller-b");
    deletion.process("unrelated", "seller-z", List.of());
    deletion.process("unrelated", "seller-z", List.of());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deleted_accounts", Long.class))
        .isEqualTo(1);
  }

  @Test
  void immutableUsernameValuesUseSameOpaqueMapping() {
    String opaque = "Immutable-US-ID-CaseSensitive";
    product("opaque", "ebay", opaque);
    deletion.process("immutable", opaque, List.of(opaque));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isZero();
  }

  @Test
  void unknownOwnershipIsNeverImportedAndLegacyRowsArePurged() {
    product("unmapped", "ebay", null);
    product("kept", "ebay", "other");
    deletion.process("legacy", "seller", List.of());
    assertThat(jdbc.queryForList("SELECT external_id FROM products", String.class))
        .containsExactly("kept");
    when(ebay.search(anyString(), anyInt()))
        .thenReturn(new EbaySearchPage(List.of(listing("no-owner", null)), false));
    assertThat(ingestion.importFashionProducts().created()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
  }

  @Test
  void missingUsernameFailsClosedAndCannotUseLegacyResume() {
    product("kept", "ebay", "seller-b");
    deletion.process("missing", "", List.of("only-user-id"));
    assertThat(
            jdbc.queryForObject("SELECT imports_paused FROM ebay_catalog_control", Boolean.class))
        .isTrue();
    assertThatThrownBy(() -> policy.resumeReviewedEmptyCatalog()).hasMessageContaining("409");
    assertThatThrownBy(() -> ingestion.importFashionProducts()).hasMessageContaining("paused");
  }

  @Test
  void keyRotationFailsClosedAndTransactionRollsBack() {
    product("kept", "ebay", "seller-a");
    tx.execute(
        status -> {
          policy.lockAndValidateKey();
          return null;
        });
    jdbc.update("UPDATE ebay_catalog_control SET hash_key_fingerprint=?", "f".repeat(64));
    assertThatThrownBy(() -> deletion.process("rotation", "seller-a", List.of()))
        .hasMessageContaining("key changed");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ebay_deletion_receipts", Long.class))
        .isZero();
  }

  @Test
  void reviewedEmptyLegacyCatalogCanResumeButPopulatedOneCannot() {
    jdbc.update(
        "UPDATE ebay_catalog_control SET imports_paused=TRUE,pause_reason='LEGACY_EMPTY_REVIEW'");
    product("kept", "ebay", "seller-a");
    assertThatThrownBy(() -> policy.resumeReviewedEmptyCatalog()).hasMessageContaining("409");
    jdbc.update("DELETE FROM products");
    policy.resumeReviewedEmptyCatalog();
    assertThat(
            jdbc.queryForObject("SELECT imports_paused FROM ebay_catalog_control", Boolean.class))
        .isFalse();
  }

  @Test
  void deletionDuringFetchCannotReintroduceDeletedSeller() throws Exception {
    var fetching = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(ebay.search(anyString(), anyInt()))
        .thenAnswer(
            call -> {
              fetching.countDown();
              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException();
              return new EbaySearchPage(List.of(listing("late", "seller-a")), false);
            });
    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var pending = executor.submit(() -> ingestion.importFashionProducts());
      assertThat(fetching.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      deletion.process("during-fetch", "seller-a", List.of());
      release.countDown();
      assertThat(pending.get(5, java.util.concurrent.TimeUnit.SECONDS).created()).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isZero();
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void adminStatusAndResumeAreAuthenticatedAndHideIdentifiers() throws Exception {
    jdbc.update(
        "UPDATE ebay_catalog_control SET imports_paused=TRUE,pause_reason='LEGACY_EMPTY_REVIEW'");
    var http = HttpClient.newHttpClient();
    String base = "http://127.0.0.1:" + port + "/api/admin/catalog";
    assertThat(
            http.send(
                    HttpRequest.newBuilder(URI.create(base + "/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    var status =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/status"))
                .header("X-Import-Token", "test-admin-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(status.statusCode()).isEqualTo(200);
    assertThat(status.body()).contains("sellerTrackingEnabled").doesNotContain("0123456789abcdef");
    var resume =
        http.send(
            HttpRequest.newBuilder(URI.create(base + "/resume-empty-legacy"))
                .header("Content-Type", "application/json")
                .header("X-Import-Token", "test-admin-token")
                .POST(
                    HttpRequest.BodyPublishers.ofString("{\"acknowledgeEmptyCatalogReview\":true}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(resume.statusCode()).isEqualTo(200);
  }
}
