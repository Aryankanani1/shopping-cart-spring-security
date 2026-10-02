package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Category writes and lookups (the caching itself is covered by CategoryIntegrationTest). */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private CategoryService categoryService;

    @Test
    void addCategory_existingName_isRejected() {
        when(categoryRepository.existsByName("Books")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.addCategory(new CategoryRequest("Books")))
                .isInstanceOf(AlreadyExistsException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategory_savesANewEntityWithOnlyTheName() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.addCategory(new CategoryRequest("Books"));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Books");
        assertThat(saved.getValue().getId()).isNull();
    }

    @Test
    void updateCategory_missing_is404() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(new CategoryRequest("Books"), 3L))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void updateCategory_renames() {
        Category category = new Category("Bokos");
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        assertThat(categoryService.updateCategory(new CategoryRequest("Books"), 3L).getName()).isEqualTo("Books");
    }

    @Test
    void deleteCategory_missing_is404() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategoryById(3L))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void findOrCreate_existing_isReturnedWithoutSaving() {
        Category books = new Category("Books");
        when(categoryRepository.findByName("Books")).thenReturn(books);

        assertThat(categoryService.findOrCreate("Books")).isSameAs(books);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void findOrCreate_missing_isCreated() {
        when(categoryRepository.findByName("Books")).thenReturn(null);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(categoryService.findOrCreate("Books").getName()).isEqualTo("Books");
    }

    @Test
    void getAllCategoryDtos_mapsIdAndName() {
        Category books = new Category("Books");
        books.setId(1L);
        when(categoryRepository.findAll()).thenReturn(List.of(books));

        assertThat(categoryService.getAllCategoryDtos())
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getName()).isEqualTo("Books");
                });
    }
}
