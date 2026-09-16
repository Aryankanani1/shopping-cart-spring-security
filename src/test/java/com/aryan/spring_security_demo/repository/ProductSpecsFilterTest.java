package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.specification.ProductSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for {@link ProductSpecs#filter} against a real JPA query. Guards the
 * regression where an all-blank filter produced a {@code null} Specification and
 * {@code findAll(Specification, Pageable)} threw
 * {@code IllegalArgumentException: Specification must not be null} (Spring Data
 * JPA 4 no longer tolerates the deprecated {@code where(null)}/{@code and(null)}
 * idiom). The unfiltered case must return every product; individual filters must
 * still narrow the result.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductSpecsFilterTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private TestEntityManager em;

    @BeforeEach
    void setUp() {
        Category electronics = em.persist(new Category("Electronics"));
        Category books = em.persist(new Category("Books"));
        em.persist(new Product("Pixel Phone", new BigDecimal("699.00"), "phone", "Google", 5, electronics));
        em.persist(new Product("Pixel Buds", new BigDecimal("199.00"), "earbuds", "Google", 8, electronics));
        em.persist(new Product("Clean Code", new BigDecimal("35.00"), "book", "Prentice", 12, books));
        em.flush();
    }

    @Test
    @DisplayName("all-blank filter returns every product (no exception, no WHERE clause)")
    void filter_allBlank_returnsAll() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter(null, "  ", ""), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("brand filter narrows to matching products")
    void filter_byBrand_narrows() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter("Google", null, null), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getBrand).containsOnly("Google");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("name prefix + category filters combine (AND)")
    void filter_byNamePrefixAndCategory_combines() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter(null, "pixel ph", "Electronics"), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Pixel Phone");
    }
}
