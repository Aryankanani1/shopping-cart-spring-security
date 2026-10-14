package com.aryan.spring_security_demo.catalog;

import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

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

    /**
     * Load these products and lock their rows (SELECT ... FOR UPDATE) until the
     * transaction ends. The rows are locked in id order, so two transactions
     * locking some of the same products take the locks in the same order and
     * can't deadlock. Checkout takes this before reading stock (OrderService).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
