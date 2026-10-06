package com.fashionsearch.controller;

import com.fashionsearch.client.EbayClientException;
import com.fashionsearch.config.ImportProperties;
import com.fashionsearch.dto.ImportResult;
import com.fashionsearch.service.ProductIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class ProductImportController {

    public static final String IMPORT_TOKEN_HEADER = "X-Import-Token";

    private final ImportProperties importProperties;

    private final ProductIngestionService productIngestionService;

    public ProductImportController(
            ImportProperties importProperties,
            ProductIngestionService productIngestionService
    ) {
        this.importProperties = importProperties;
        this.productIngestionService = productIngestionService;
    }

    @PostMapping("/api/admin/products/import")
    public ResponseEntity<ImportResult> importProducts(
            @RequestHeader(name = IMPORT_TOKEN_HEADER, required = false) String providedToken
    ) {
        if (!tokenMatches(providedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(productIngestionService.importFashionProducts());
    }

    @ExceptionHandler(EbayClientException.class)
    public ResponseEntity<String> ebayFailed(EbayClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
    }

    private boolean tokenMatches(String providedToken) {
        String expected = importProperties.getToken();
        if (expected == null || expected.isBlank() || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}
