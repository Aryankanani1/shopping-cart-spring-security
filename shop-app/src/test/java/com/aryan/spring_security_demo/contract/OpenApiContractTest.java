package com.aryan.spring_security_demo.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API's contract, {@code docs/openapi.json}: the OpenAPI spec the app
 * generates, committed. A change to the API then shows up in its pull request as
 * a diff of this file, where a frontend change it breaks is easy to spot; and the
 * staging stage fuzzes the running API against it (Schemathesis), which needs the
 * spec although production doesn't serve it.
 *
 * <p>When the API changes on purpose, regenerate it:
 * <pre>
 *   ./mvnw test -pl shop-app -am -Dtest=OpenApiContractTest -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true
 * </pre>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    private static final Path CONTRACT = repositoryRoot().resolve("docs/openapi.json");
    private static final String REGENERATE = "./mvnw test -pl shop-app -am -Dtest=OpenApiContractTest "
            + "-Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("docs/openapi.json is the API as it is")
    void theCommittedContract_matchesTheApi() throws Exception {
        JsonNode generated = generatedSpec();

        if (Boolean.getBoolean("openapi.update")) {
            write(generated);
            return;
        }
        assertThat(CONTRACT).as("docs/openapi.json is missing; generate it with: " + REGENERATE).exists();
        // Compared as JSON trees, so the order of keys doesn't matter.
        assertThat(generated)
                .as("The API no longer matches docs/openapi.json. If the change is intended, regenerate it with: "
                        + REGENERATE)
                .isEqualTo(objectMapper.readTree(Files.readString(CONTRACT)));
    }

    /** The spec as the app serves it, without the server URL (which is wherever it runs). */
    private JsonNode generatedSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode spec = (ObjectNode) objectMapper.readTree(body);
        spec.remove("servers");
        return spec;
    }

    /** Pretty-printed with keys sorted, so a regenerated file diffs cleanly. */
    private void write(JsonNode spec) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(objectMapper.treeToValue(spec, Object.class));
        Files.writeString(CONTRACT, json + System.lineSeparator());
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("mvnw"))) {
                return dir;
            }
        }
        throw new IllegalStateException("No mvnw above " + Path.of("").toAbsolutePath());
    }
}
