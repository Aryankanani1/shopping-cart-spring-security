package com.aryan.spring_security_demo.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product table holds what the API accepts. Runs on MySQL with the Flyway
 * schema, so it checks the V10 migration that widens the description column.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("a description of the longest accepted length is stored whole")
    void longestDescription_isStored() {
        // The integration tests share one database: a category name no other test uses.
        Category category = categoryRepository.save(new Category("Lighting " + UUID.randomUUID()));
        String description = "d".repeat(Product.DESCRIPTION_MAX);

        Product saved = productRepository.saveAndFlush(
                new Product("Desk Lamp", new BigDecimal("24.00"), description, "Lumen", 3, category));
        em.clear(); // read the row back from MySQL, not the cached entity

        assertThat(productRepository.findById(saved.getId()).orElseThrow().getDescription())
                .hasSize(Product.DESCRIPTION_MAX);
    }
}
