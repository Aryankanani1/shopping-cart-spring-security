package com.aryan.spring_security_demo.repository.specification;

import com.aryan.spring_security_demo.model.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic, composable filters for product queries. Each builder returns
 * {@link Specification#unrestricted()} (a no-op that adds no predicate) when its
 * argument is absent, and {@link Specification#allOf(Specification[])} ANDs them
 * together — so a request with only some filters present yields a WHERE clause
 * with only the predicates that matter, and an unfiltered request yields a valid,
 * non-null Specification with no WHERE clause at all. One code path, any subset of
 * filters, no per-combination repository methods.
 *
 * <p>{@code unrestricted()}/{@code allOf} replace the deprecated
 * {@code where(null)}/{@code and(null)} idiom: as of Spring Data JPA 4,
 * {@link Specification#and(Specification)} rejects a {@code null} operand, so the
 * old "return {@code null} to mean no constraint" approach collapsed an all-blank
 * filter to a {@code null} Specification and made {@code findAll} throw
 * {@code IllegalArgumentException: Specification must not be null}.
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> filter(String brand, String name, String category) {
        return Specification.allOf(
                brandEquals(brand),
                nameStartsWith(name),
                categoryEquals(category));
    }

    /** Exact match on the indexed {@code brand} column. */
    private static Specification<Product> brandEquals(String brand) {
        return isBlank(brand) ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("brand"), brand);
    }

    /**
     * Case-insensitive prefix match. A trailing-only wildcard ({@code name%}) keeps
     * the {@code name} index usable — a leading wildcard ({@code %name%}) would force
     * a full scan.
     */
    private static Specification<Product> nameStartsWith(String name) {
        return isBlank(name) ? Specification.unrestricted()
                : (root, query, cb) -> cb.like(cb.lower(root.get("name")), name.toLowerCase() + "%");
    }

    /** Filter by category name via the to-one join (no row multiplication). */
    private static Specification<Product> categoryEquals(String category) {
        return isBlank(category) ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("category").get("name"), category);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
