package com.aryan.spring_security_demo.journey;

import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin image upload end to end: multipart upload → the stored download URL →
 * fetching the image back through that URL. Runs the real stack against H2; the
 * admin role comes from {@link WithMockUser} (only the authority matters to the
 * {@code POST /images} edge rule).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ImageUploadIntegrationTest {

    // Not a real PNG — the API checks the declared type, not the bytes.
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
        productId = productRepository.save(new Product(
                "Desk Lamp", new BigDecimal("24.00"), "LED lamp", "Lumo", 3, category)).getId();
    }

    @Test
    @DisplayName("upload → downloadUrl points at the download endpoint and serves the image")
    void upload_returnsWorkingDownloadUrl() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("files", "lamp.png", "image/png", PNG_BYTES))
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode image = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").get(0);
        long imageId = image.path("imageId").asLong();
        String downloadUrl = image.path("downloadUrl").asText();
        assertThat(downloadUrl).isEqualTo("/api/v1/images/" + imageId);

        // The URL the API hands out actually serves the bytes that were uploaded.
        mockMvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG_BYTES));

        // And the product exposes the same URL to the storefront.
        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(jsonPath("$.data.images[0].downloadUrl").value(downloadUrl));
    }

    @Test
    @DisplayName("a non-image upload is a 400 and nothing is stored")
    void upload_nonImage_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("files", "lamp.png", "image/png", PNG_BYTES))
                        .file(new MockMultipartFile("files", "page.html", "text/html", "<script>".getBytes()))
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid image"))
                .andExpect(jsonPath("$.detail").value(
                        "page.html is not a supported image (use JPEG, PNG, WebP or GIF)"));

        // One bad file rejects the whole batch — the valid PNG isn't saved either.
        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(jsonPath("$.data.images").isEmpty());
    }

    @Test
    @DisplayName("an empty file is a 400")
    void upload_emptyFile_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("files", "blank.png", "image/png", new byte[0]))
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("blank.png is empty"));
    }
}
