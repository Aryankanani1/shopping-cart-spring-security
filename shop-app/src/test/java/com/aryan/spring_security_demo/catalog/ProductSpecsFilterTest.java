package com.aryan.spring_security_demo.catalog;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

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
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductSpecsFilterTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private TestEntityManager em;

    // The integration tests share one database, so other tests' products may be there:
    // this test's categories and brand are its own, and counts are relative.
    private final String electronicsName = "Electronics " + UUID.randomUUID();
    private final String googleBrand = "Google " + UUID.randomUUID();
    private final String lumenBrand = "Lumen " + UUID.randomUUID();
    private long productsBefore;

    @BeforeEach
    void setUp() {
        productsBefore = productRepository.count();
        Category electronics = em.persist(new Category(electronicsName));
        Category books = em.persist(new Category("Books " + UUID.randomUUID()));
        em.persist(new Product("Pixel Phone", new BigDecimal("699.00"), "phone", googleBrand, 5, electronics));
        em.persist(new Product("Pixel Buds", new BigDecimal("199.00"), "earbuds", googleBrand, 8, electronics));
        em.persist(new Product("Clean Code", new BigDecimal("35.00"), "book", "Prentice", 12, books));
        em.flush();
    }

    @Test
    @DisplayName("all-blank filter returns every product (no exception, no WHERE clause)")
    void filter_allBlank_returnsAll() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter(null, "  ", ""), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(productsBefore + 3);
    }

    @Test
    @DisplayName("brand filter narrows to matching products")
    void filter_byBrand_narrows() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter(googleBrand, null, null), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getBrand).containsOnly(googleBrand);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("name prefix + category filters combine (AND)")
    void filter_byNamePrefixAndCategory_combines() {
        Page<Product> page = productRepository.findAll(
                ProductSpecs.filter(null, "pixel ph", electronicsName), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Pixel Phone");
    }

    @Test
    @DisplayName("the name search ignores case")
    void filter_byName_ignoresCase() {
        assertThat(names(ProductSpecs.filter(googleBrand, "PIXEL", null)))
                .containsExactlyInAnyOrder("Pixel Phone", "Pixel Buds");
    }

    // Regression: % and _ in the search text were LIKE wildcards, so "%phone" ran
    // as a contains search (a full scan) and "_ixel" matched "Pixel". Each search is
    // within this test's own brand, which holds the products that used to match.
    @Test
    @DisplayName("wildcards typed into the name search match literally")
    void filter_byName_treatsWildcardsLiterally() {
        em.persist(new Product("50% Off Lamp", new BigDecimal("20.00"), "lamp", lumenBrand, 3, null));
        em.persist(new Product("500 Watt Bulb", new BigDecimal("5.00"), "bulb", lumenBrand, 3, null));
        em.persist(new Product("Wow! Speaker", new BigDecimal("49.00"), "speaker", lumenBrand, 3, null));
        em.flush();

        assertThat(names(ProductSpecs.filter(googleBrand, "%phone", null))).isEmpty();
        assertThat(names(ProductSpecs.filter(googleBrand, "_ixel", null))).isEmpty();
        assertThat(names(ProductSpecs.filter(lumenBrand, "50%", null))).containsExactly("50% Off Lamp");
        // The escape character itself is matched literally too.
        assertThat(names(ProductSpecs.filter(lumenBrand, "wow!", null))).containsExactly("Wow! Speaker");
    }

    private List<String> names(Specification<Product> spec) {
        return productRepository.findAll(spec, PageRequest.of(0, 20)).map(Product::getName).getContent();
    }
}
