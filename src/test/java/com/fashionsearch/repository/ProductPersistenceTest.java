package com.fashionsearch.repository;

import com.fashionsearch.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProductPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteProducts() {
        productRepository.deleteAll();
    }

    @Test
    void enablesPgvectorExtension() {
        String extension = jdbcTemplate.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'",
                String.class);

        assertThat(extension).isEqualTo("vector");
    }

    @Test
    void savesAndReadsProductById() {
        Product saved = productRepository.saveAndFlush(product("ebay", "item-1"));

        Product loaded = productRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getExternalId()).isEqualTo("item-1");
        assertThat(loaded.getTitle()).isEqualTo("Black Cropped Jacket");
        assertThat(loaded.getDescription()).isEqualTo("Fitted leather jacket");
        assertThat(loaded.getCategory()).isEqualTo("jacket");
        assertThat(loaded.getPrice()).isEqualByComparingTo(new BigDecimal("129.00"));
        assertThat(loaded.getCurrency()).isEqualTo("USD");
        assertThat(loaded.getImageUrl()).isEqualTo("https://example.com/jacket.jpg");
        assertThat(loaded.getProductUrl()).isEqualTo("https://example.com/jacket");
        assertThat(loaded.getSource()).isEqualTo("ebay");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsProductBySourceAndExternalId() {
        productRepository.saveAndFlush(product("ebay", "item-1"));

        Optional<Product> found = productRepository.findBySourceAndExternalId("ebay", "item-1");
        Optional<Product> missing = productRepository.findBySourceAndExternalId("ebay", "missing");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getTitle()).isEqualTo("Black Cropped Jacket");
        assertThat(missing).isEmpty();
    }

    @Test
    void rejectsDuplicateSourceAndExternalId() {
        productRepository.saveAndFlush(product("ebay", "item-1"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product("ebay", "item-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameExternalIdFromDifferentSource() {
        Product ebay = productRepository.saveAndFlush(product("ebay", "item-1"));
        Product other = productRepository.saveAndFlush(product("other", "item-1"));

        assertThat(ebay.getId()).isNotEqualTo(other.getId());
        assertThat(productRepository.findBySourceAndExternalId("ebay", "item-1")).isPresent();
        assertThat(productRepository.findBySourceAndExternalId("other", "item-1")).isPresent();
    }

    private Product product(String source, String externalId) {
        Product product = new Product();
        product.setSource(source);
        product.setExternalId(externalId);
        product.setTitle("Black Cropped Jacket");
        product.setDescription("Fitted leather jacket");
        product.setCategory("jacket");
        product.setPrice(new BigDecimal("129.00"));
        product.setCurrency("USD");
        product.setImageUrl("https://example.com/jacket.jpg");
        product.setProductUrl("https://example.com/jacket");
        return product;
    }
}
