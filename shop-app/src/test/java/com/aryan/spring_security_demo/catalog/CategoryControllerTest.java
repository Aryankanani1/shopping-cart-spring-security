package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice for {@link CategoryController}'s reads: the list and the lookup by name
 * share {@code GET /categories} (one operation in the spec, with the name
 * optional), and any id reaches {@code GET /categories/{id}}, as it does PUT and
 * DELETE. Security filters are off; ShopConfig opens these reads to everyone.
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryServiceInterface categoryService;

    @Test
    void withoutAName_listsEveryCategory() throws Exception {
        when(categoryService.getAllCategoryDtos()).thenReturn(List.of(dto(1L, "Books"), dto(2L, "Garden")));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Garden"));
        verify(categoryService, never()).getCategoryByName(any());
    }

    @Test
    void withAName_findsThatCategory() throws Exception {
        Category books = new Category();
        books.setId(1L);
        books.setName("Books");
        when(categoryService.getCategoryByName("Books")).thenReturn(books);
        when(categoryService.convertToDto(books)).thenReturn(dto(1L, "Books"));

        mockMvc.perform(get("/api/v1/categories").param("name", "Books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Books"));
        verify(categoryService, never()).getAllCategoryDtos();
    }

    @Test
    void anUnknownName_is404() throws Exception {
        when(categoryService.getCategoryByName("Nope")).thenThrow(new ResourceNotFoundException("Category not found!"));

        mockMvc.perform(get("/api/v1/categories").param("name", "Nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNegativeId_isLookedUp_andIs404() throws Exception {
        when(categoryService.getCategoryById(anyLong())).thenThrow(new ResourceNotFoundException("Category not found!"));

        mockMvc.perform(get("/api/v1/categories/{id}", -1))
                .andExpect(status().isNotFound());
        verify(categoryService).getCategoryById(-1L);
    }

    @Test
    void anIdThatIsntANumber_is400() throws Exception {
        mockMvc.perform(get("/api/v1/categories/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    private static CategoryDto dto(Long id, String name) {
        CategoryDto dto = new CategoryDto();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }
}
