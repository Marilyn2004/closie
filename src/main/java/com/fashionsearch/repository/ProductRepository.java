package com.fashionsearch.repository;

import com.fashionsearch.model.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository
    extends JpaRepository<Product, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {

  Optional<Product> findBySourceAndExternalId(String source, String externalId);
}
