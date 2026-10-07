package com.aryan.spring_security_demo.catalog;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Category names are unique in the database, not just checked by the service:
 * two requests racing past the service check still can't both win. (The MySQL
 * migration, V6, adds the same constraint; this runs on the H2 schema built
 * from the entity.)
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired private CategoryRepository categoryRepository;

    // The integration tests share one database, so other tests' categories may be
    // there: use names no other test (or earlier run) has.
    private final String gardeningName = "Gardening " + UUID.randomUUID();
    private final String toolsName = "Tools " + UUID.randomUUID();

    @Test
    @DisplayName("a second category with the same name is rejected by the database")
    void duplicateName_violatesTheUniqueConstraint() {
        categoryRepository.saveAndFlush(new Category(gardeningName));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category(gardeningName)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByNameAndIdNot ignores the category being renamed")
    void existsByNameAndIdNot_excludesItself() {
        Category gardening = categoryRepository.saveAndFlush(new Category(gardeningName));
        Category tools = categoryRepository.saveAndFlush(new Category(toolsName));

        assertThat(categoryRepository.existsByNameAndIdNot(gardeningName, gardening.getId())).isFalse();
        assertThat(categoryRepository.existsByNameAndIdNot(gardeningName, tools.getId())).isTrue();
    }
}
