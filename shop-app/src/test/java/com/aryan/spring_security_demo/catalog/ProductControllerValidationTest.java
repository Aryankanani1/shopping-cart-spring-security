package com.aryan.spring_security_demo.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation slice for {@link ProductController}. Security filters are disabled
 * so we exercise Bean Validation in isolation — a bad body must never reach the
 * service. The @PreAuthorize checks are not loaded in this slice.
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductServiceInterface productService;

    @Test
    void addProduct_withBlankNameAndMissingFields_returns400() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.category").exists());
    }

    @Test
    void addProduct_withProfaneName_returns400() throws Exception {
        String body = """
                {
                  "name": "spam widget",
                  "price": 10.00,
                  "brand": "Acme",
                  "inventory": 5,
                  "category": { "name": "Books" }
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Product name contains disallowed words"));
    }

    @Test
    void addProduct_withNegativePrice_returns400() throws Exception {
        String body = """
                {
                  "name": "Keyboard",
                  "price": -1,
                  "brand": "Acme",
                  "inventory": 5,
                  "category": { "name": "Electronics" }
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.price").value("Price must be greater than zero"));
    }

    // Regression: the columns are varchar(255), so a longer value got past
    // validation and came back from the database as a 409 "Data conflict".
    @Test
    void addProduct_withTextLongerThanItsColumn_returns400() throws Exception {
        String body = """
                {
                  "name": "%1$s",
                  "price": 10.00,
                  "brand": "%1$s",
                  "description": "%1$s",
                  "inventory": 5,
                  "category": { "name": "%1$s" }
                }
                """.formatted("a".repeat(256));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Product name must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.brand").value("Brand must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.description").value("Description must be at most 255 characters"))
                .andExpect(jsonPath("$.errors['category.name']").value("Category name must be at most 255 characters"));
    }

    @Test
    void updateProduct_withTextLongerThanItsColumn_returns400() throws Exception {
        String body = """
                {
                  "name": "%1$s",
                  "price": 10.00,
                  "brand": "%1$s",
                  "description": "%1$s",
                  "inventory": 5
                }
                """.formatted("a".repeat(256));

        mockMvc.perform(put("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Product name must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.brand").value("Brand must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.description").value("Description must be at most 255 characters"));
    }
}
