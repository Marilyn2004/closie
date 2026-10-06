package com.fashionsearch.service;

import com.fashionsearch.client.EbayClient;
import com.fashionsearch.client.EbaySearchPage;
import com.fashionsearch.config.EbayProperties;
import com.fashionsearch.dto.EbayProduct;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.model.Product;
import com.fashionsearch.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIngestionServiceTest {

    private final EbayClient ebayClient = mock(EbayClient.class);

    private final ProductRepository productRepository = mock(ProductRepository.class);

    private final EbayProperties ebayProperties = new EbayProperties();

    private final Map<String, Product> stored = new HashMap<>();

    private ProductIngestionService service;

    @BeforeEach
    void setUp() {
        ebayProperties.setSearchKeywords(List.of("leather jacket"));
        ebayProperties.setPageSize(50);
        ebayProperties.setMaxPages(2);
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        service = new ProductIngestionService(ebayClient, ebayProperties, productRepository, transactions);
        when(ebayClient.search(anyString(), anyInt())).thenReturn(new EbaySearchPage(List.of(), false));
        when(productRepository.findBySourceAndExternalId(eq(ProductIngestionService.EBAY_SOURCE), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            stored.put(product.getExternalId(), product);
            return product;
        });
    }

    @Test
    void insertsANewListing() {
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|1|0", "Black Jacket", "fitted", "Jackets", "129.5", "usd", "https://img", "https://item")
        ), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result).isEqualTo(new ImportResult(1, 0, 0));
        Product saved = stored.get("v1|1|0");
        assertThat(saved.getSource()).isEqualTo("ebay");
        assertThat(saved.getTitle()).isEqualTo("Black Jacket");
        assertThat(saved.getDescription()).isEqualTo("fitted");
        assertThat(saved.getCategory()).isEqualTo("Jackets");
        assertThat(saved.getPrice()).isEqualByComparingTo("129.50");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getImageUrl()).isEqualTo("https://img");
        assertThat(saved.getProductUrl()).isEqualTo("https://item");
    }

    @Test
    void updatesAnExistingListingWhenPriceOrTitleChanges() {
        stored.put("v1|1|0", existing("v1|1|0", "Black Jacket", new BigDecimal("129.50")));
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|1|0", "Cropped Jacket", null, "Jackets", "99.00", "USD", null, null)
        ), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result).isEqualTo(new ImportResult(0, 1, 0));
        assertThat(stored.get("v1|1|0").getTitle()).isEqualTo("Cropped Jacket");
        assertThat(stored.get("v1|1|0").getPrice()).isEqualByComparingTo("99.00");
        assertThat(stored.get("v1|1|0").getDescription()).isNull();
    }

    @Test
    void leavesAnUnchangedListingInPlace() {
        stored.put("v1|1|0", existing("v1|1|0", "Black Jacket", new BigDecimal("80.00")));
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|1|0", "Black Jacket", null, null, "80.0", "USD", null, null)
        ), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result).isEqualTo(new ImportResult(0, 0, 0));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsListingsThatCannotBeStored() {
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing(" ", "Title", null, null, "10.00", "USD", null, null),
                listing("v1|2|0", " ", null, null, "10.00", "USD", null, null),
                listing("v1|3|0", "No Price", null, null, null, "USD", null, null),
                listing("v1|4|0", "Bad Currency", null, null, "10.00", "US", null, null),
                listing("v1|5|0", "Negative", null, null, "-1.00", "USD", null, null)
        ), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result).isEqualTo(new ImportResult(0, 0, 5));
        assertThat(stored).isEmpty();
    }

    @Test
    void storesALongCategoryAsNullAndStillImportsTheProduct() {
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|9|0", "Coat", null, "x".repeat(256), "20.00", "USD", null, null)
        ), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result.created()).isEqualTo(1);
        assertThat(stored.get("v1|9|0").getCategory()).isNull();
    }

    @Test
    void stopsAfterTheConfiguredNumberOfPages() {
        ebayProperties.setMaxPages(2);
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|1|0", "Page One", null, null, "10.00", "USD", null, null)
        ), true));
        when(ebayClient.search("leather jacket", 50)).thenReturn(new EbaySearchPage(List.of(
                listing("v1|2|0", "Page Two", null, null, "11.00", "USD", null, null)
        ), true));

        ImportResult result = service.importFashionProducts();

        assertThat(result.created()).isEqualTo(2);
        verify(ebayClient, never()).search("leather jacket", 100);
    }

    @Test
    void importsTheSameItemIdFromTwoKeywordsOnce() {
        ebayProperties.setSearchKeywords(List.of("leather jacket", "cardigan"));
        EbayProduct shared = listing("v1|7|0", "Shared Jacket", null, "Jackets", "50.00", "USD", null, null);
        when(ebayClient.search("leather jacket", 0)).thenReturn(new EbaySearchPage(List.of(shared), false));
        when(ebayClient.search("cardigan", 0)).thenReturn(new EbaySearchPage(List.of(shared), false));

        ImportResult result = service.importFashionProducts();

        assertThat(result).isEqualTo(new ImportResult(1, 0, 0));
        assertThat(stored).hasSize(1);
    }

    private EbayProduct listing(
            String itemId,
            String title,
            String description,
            String category,
            String price,
            String currency,
            String imageUrl,
            String itemWebUrl
    ) {
        return new EbayProduct(
                itemId,
                title,
                description,
                category,
                price == null ? null : new BigDecimal(price),
                currency,
                imageUrl,
                itemWebUrl
        );
    }

    private Product existing(String externalId, String title, BigDecimal price) {
        Product product = new Product();
        product.setSource(ProductIngestionService.EBAY_SOURCE);
        product.setExternalId(externalId);
        product.setTitle(title);
        product.setPrice(price);
        product.setCurrency("USD");
        return product;
    }
}
