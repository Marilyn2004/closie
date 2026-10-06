package com.fashionsearch.service;

import com.fashionsearch.client.EbayClient;
import com.fashionsearch.client.EbaySearchPage;
import com.fashionsearch.dto.EbayProduct;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProductIngestionPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private ProductIngestionService productIngestionService;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private EbayClient ebayClient;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        when(ebayClient.search(anyString(), anyInt())).thenReturn(new EbaySearchPage(java.util.List.of(), false));
    }

    @Test
    void persistsImportedListingsAndUpdatesThemInPlace() {
        AtomicReference<EbayProduct> listing = new AtomicReference<>(
                listing("v1|100|0", "Wool Cardigan", "80.00")
        );
        when(ebayClient.search(eq("cardigan"), eq(0))).thenAnswer(invocation ->
                new EbaySearchPage(java.util.List.of(listing.get()), false));

        ImportResult first = productIngestionService.importFashionProducts();
        Product stored = productRepository.findBySourceAndExternalId("ebay", "v1|100|0").orElseThrow();
        Long id = stored.getId();
        Instant createdAt = stored.getCreatedAt();

        listing.set(listing("v1|100|0", "Ivory Cardigan", "72.00"));
        ImportResult second = productIngestionService.importFashionProducts();
        listing.set(listing("v1|100|0", "Ivory Cardigan", "72.00"));
        ImportResult third = productIngestionService.importFashionProducts();

        Product updated = productRepository.findBySourceAndExternalId("ebay", "v1|100|0").orElseThrow();
        assertThat(first).isEqualTo(new ImportResult(1, 0, 0));
        assertThat(second).isEqualTo(new ImportResult(0, 1, 0));
        assertThat(third).isEqualTo(new ImportResult(0, 0, 0));
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getTitle()).isEqualTo("Ivory Cardigan");
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("72.00"));
        assertThat(updated.getSource()).isEqualTo("ebay");
    }

    @Test
    void storesOneRowWhenTwoKeywordsReturnTheSameItem() {
        EbayProduct shared = listing("v1|200|0", "Black Dress", "65.00");
        when(ebayClient.search(eq("dress"), eq(0))).thenReturn(new EbaySearchPage(java.util.List.of(shared), false));
        when(ebayClient.search(eq("leather jacket"), eq(0)))
                .thenReturn(new EbaySearchPage(java.util.List.of(shared), false));

        ImportResult result = productIngestionService.importFashionProducts();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void skipsAnInvalidListingAndStoresTheValidOne() {
        when(ebayClient.search(eq("sneakers"), eq(0))).thenReturn(new EbaySearchPage(java.util.List.of(
                new EbayProduct("v1|300|0", "Runner", null, "Shoes", null, "USD", null, null),
                listing("v1|301|0", "Court Sneaker", "55.00")
        ), false));

        ImportResult result = productIngestionService.importFashionProducts();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(productRepository.findBySourceAndExternalId("ebay", "v1|301|0")).isPresent();
        assertThat(productRepository.findBySourceAndExternalId("ebay", "v1|300|0")).isEmpty();
    }

    private EbayProduct listing(String itemId, String title, String price) {
        return new EbayProduct(
                itemId,
                title,
                "soft knit",
                "Sweaters",
                new BigDecimal(price),
                "USD",
                "https://example.com/" + itemId + ".jpg",
                "https://www.ebay.com/itm/" + itemId
        );
    }
}
