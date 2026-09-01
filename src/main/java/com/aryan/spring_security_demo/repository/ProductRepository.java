package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link JpaSpecificationExecutor} adds {@code findAll(Specification, Pageable)},
 * which powers the single paginated + dynamically filtered listing endpoint —
 * replacing the former one-method-per-filter-combination approach.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // convertToDto reads product.category.name. category is a to-one, so fetch
    // it with the list (single round trip, no duplicate rows, no DISTINCT).
    // Without this, a global default_batch_fetch_size still caps it, but at the
    // cost of one extra batched query — this collapses that into the main query.
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"category"})
    List<Product> findAll();

    Long countByBrandAndName(String brand, String name);

    boolean existsByNameAndBrand(String name, String brand);
}
