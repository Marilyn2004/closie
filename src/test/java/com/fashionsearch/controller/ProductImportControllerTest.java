package com.fashionsearch.controller;

import com.fashionsearch.client.EbayClientException;
import com.fashionsearch.config.ImportProperties;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.service.ProductIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductImportController.class)
@EnableConfigurationProperties(ImportProperties.class)
@TestPropertySource(properties = "import.token=test-token")
class ProductImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductIngestionService productIngestionService;

    @Test
    void importsWhenTheTokenMatches() throws Exception {
        when(productIngestionService.importFashionProducts()).thenReturn(new ImportResult(2, 1, 3));

        mockMvc.perform(post("/api/admin/products/import")
                        .header(ProductImportController.IMPORT_TOKEN_HEADER, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(3));
    }

    @Test
    void rejectsAMissingOrWrongToken() throws Exception {
        mockMvc.perform(post("/api/admin/products/import"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/products/import")
                        .header(ProductImportController.IMPORT_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());

        verify(productIngestionService, never()).importFashionProducts();
    }

    @Test
    void rejectsImportWhenTheTokenIsNotConfigured() {
        ImportProperties properties = new ImportProperties();
        properties.setToken(" ");
        ProductImportController controller = new ProductImportController(properties, productIngestionService);

        ResponseEntity<ImportResult> response = controller.importProducts("test-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(productIngestionService, never()).importFashionProducts();
    }

    @Test
    void returnsBadGatewayWhenEbayFails() throws Exception {
        when(productIngestionService.importFashionProducts())
                .thenThrow(new EbayClientException("eBay search failed with HTTP 429"));

        mockMvc.perform(post("/api/admin/products/import")
                        .header(ProductImportController.IMPORT_TOKEN_HEADER, "test-token"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string("eBay search failed with HTTP 429"));
    }
}
