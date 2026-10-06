package com.aryan.spring_security_demo.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * {@link JpaSpecificationExecutor} adds {@code findAll(Specification, Pageable)},
 * which powers the single paginated + dynamically filtered listing endpoint —
 * replacing the former one-method-per-filter-combination approach.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // The product listing (ProductService.findProducts). convertToDto reads
    // product.category.name; category is a to-one, so fetch it with the page
    // (single round trip, no duplicate rows, paging still done by the database).
    // Without this, default_batch_fetch_size still caps it, but at the cost of
    // one extra batched query; this folds that into the main query.
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    Long countByBrandAndName(String brand, String name);

    boolean existsByNameAndBrand(String name, String brand);
}
