package com.aryan.spring_security_demo.repository.specification;

import com.aryan.spring_security_demo.model.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic, composable filters for product queries. Each builder returns
 * {@code null} when its argument is absent, and {@link Specification#and(Specification)}
 * treats a {@code null} operand as "no constraint" — so a request with only some
 * filters present yields a WHERE clause with only the predicates that matter, and
 * an unfiltered request yields no WHERE clause at all. One code path, any subset
 * of filters, no per-combination repository methods.
 */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> filter(String brand, String name, String category) {
        return Specification.where(brandEquals(brand))
                .and(nameStartsWith(name))
                .and(categoryEquals(category));
    }

    /** Exact match on the indexed {@code brand} column. */
    private static Specification<Product> brandEquals(String brand) {
        return isBlank(brand) ? null
                : (root, query, cb) -> cb.equal(root.get("brand"), brand);
    }

    /**
     * Case-insensitive prefix match. A trailing-only wildcard ({@code name%}) keeps
     * the {@code name} index usable — a leading wildcard ({@code %name%}) would force
     * a full scan.
     */
    private static Specification<Product> nameStartsWith(String name) {
        return isBlank(name) ? null
                : (root, query, cb) -> cb.like(cb.lower(root.get("name")), name.toLowerCase() + "%");
    }

    /** Filter by category name via the to-one join (no row multiplication). */
    private static Specification<Product> categoryEquals(String category) {
        return isBlank(category) ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("name"), category);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
