package com.fashionsearch.repository;

import com.fashionsearch.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {

    Optional<Product> findBySourceAndExternalId(String source, String externalId);
}
