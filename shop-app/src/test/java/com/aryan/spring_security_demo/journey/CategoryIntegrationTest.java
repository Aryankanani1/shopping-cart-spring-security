package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.catalog.Category;
import com.aryan.spring_security_demo.catalog.CategoryRepository;
import com.aryan.spring_security_demo.common.config.CacheConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Category writes and the cached category list: the list is served from the
 * {@code categories} cache, and every write through the API refreshes it.
 * Category names are unique per test, since the table is shared with other
 * test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class CategoryIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CacheManager cacheManager;

    private final List<String> createdNames = new ArrayList<>();

    // The cache outlives a test (one Spring context), so start each test cold.
    @BeforeEach
    void clearCache() {
        cacheManager.getCache(CacheConfig.CATEGORIES_CACHE).clear();
    }

    // Leave the shared category table and the cache as other test classes expect them.
    @AfterEach
    void cleanUp() {
        createdNames.stream()
                .map(categoryRepository::findByName)
                .filter(Objects::nonNull)
                .forEach(categoryRepository::delete);
        clearCache();
    }

    @Test
    @DisplayName("the list is served from the cache: a row written behind the API's back doesn't show")
    void list_isCached() throws Exception {
        listNames().andExpect(status().isOk());  // fills the cache
        String hidden = unique("Hidden");
        categoryRepository.save(new Category(hidden));

        listNames().andExpect(jsonPath("$.data[*].name", not(hasItem(hidden))));
    }

    @Test
    @DisplayName("create, rename and delete through the API each refresh the cached list")
    void writes_refreshTheList() throws Exception {
        listNames();  // fills the cache
        String name = unique("Garden");

        String body = create(name).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("data").path("id").asLong();
        listNames().andExpect(jsonPath("$.data[*].name", hasItem(name)));

        String renamed = unique("Outdoor");
        mockMvc.perform(put("/api/v1/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + renamed + "\"}"))
                .andExpect(status().isOk());
        listNames()
                .andExpect(jsonPath("$.data[*].name", hasItem(renamed)))
                .andExpect(jsonPath("$.data[*].name", not(hasItem(name))));

        mockMvc.perform(delete("/api/v1/categories/{id}", id)).andExpect(status().isNoContent());
        listNames().andExpect(jsonPath("$.data[*].name", not(hasItem(renamed))));
    }

    @Test
    @DisplayName("a client can't choose the id of a new category")
    void create_ignoresClientId() throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 424242, \"version\": 7, \"name\": \"" + unique("Toys") + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).path("data").path("id").asLong()).isNotEqualTo(424242L);
    }

    @Test
    @DisplayName("a blank name is a 400 on the name field")
    void create_blankName_isRejected() throws Exception {
        create(" ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("a customer can't create a category")
    void create_byCustomer_isForbidden() throws Exception {
        create(unique("Nope")).andExpect(status().isForbidden());
    }

    private ResultActions create(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}"));
    }

    private ResultActions listNames() throws Exception {
        return mockMvc.perform(get("/api/v1/categories"));
    }

    private String unique(String prefix) {
        String name = prefix + " " + UUID.randomUUID().toString().substring(0, 8);
        createdNames.add(name);
        return name;
    }
}
