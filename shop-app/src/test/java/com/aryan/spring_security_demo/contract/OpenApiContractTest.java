package com.aryan.spring_security_demo.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.MissingNode;
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
import java.util.regex.Pattern;

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

    // The staging fuzzing (Schemathesis) generates its requests from the spec. Each
    // of these was documented looser than the API is, so it spent its requests on
    // ones the API refuses before reaching the code behind them.

    @Test
    @DisplayName("GET /categories lists them without a name")
    void listingCategories_needsNoName() throws Exception {
        JsonNode parameters = generatedSpec().at("/paths/~1api~1v1~1categories/get/parameters");

        assertThat(parameters).as("GET /categories has its name parameter").isNotEmpty();
        for (JsonNode parameter : parameters) {
            assertThat(parameter.path("required").asBoolean(false)).as(parameter.toString()).isFalse();
        }
    }

    @Test
    @DisplayName("a @NotBlank field can't be empty or blank")
    void aNotBlankField_cantBeBlank() throws Exception {
        // @NotBlank and @Size(max = 255), like most request fields: its minLength
        // is @Size's 0, so the pattern is what rules out "" and "   ".
        JsonNode firstName = generatedSpec().at("/components/schemas/CreateUserRequest/properties/firstName");

        assertThat(firstName.path("maxLength").asInt()).as(firstName.toString()).isEqualTo(255);
        Pattern pattern = Pattern.compile(firstName.path("pattern").asText());
        assertThat(pattern.matcher("").find()).as("an empty name matches " + pattern).isFalse();
        assertThat(pattern.matcher(" \t\u2003").find()).as("a blank name matches " + pattern).isFalse();
        assertThat(pattern.matcher(" Ada ").find()).as("a name matches " + pattern).isTrue();
    }

    @Test
    @DisplayName("an Idempotency-Key is matched whole")
    void theIdempotencyKey_isMatchedWhole() throws Exception {
        JsonNode spec = generatedSpec();
        for (String operation : new String[] {"/paths/~1api~1v1~1orders~1{orderId}~1payments/post",
                "/paths/~1api~1v1~1orders~1{orderId}~1refunds/post"}) {
            JsonNode header = parameter(spec.at(operation), "Idempotency-Key");
            Pattern pattern = Pattern.compile(header.at("/schema/pattern").asText());
            assertThat(pattern.matcher("3f2b9c1e-8d4a-4f6b").find()).as(operation).isTrue();
            assertThat(pattern.matcher("!!" + "a".repeat(8) + "!!").find()).as(operation).isFalse();
        }
    }

    @Test
    @DisplayName("image uploads are multipart forms")
    void imageUploads_areMultipartForms() throws Exception {
        JsonNode spec = generatedSpec();
        JsonNode upload = spec.at("/paths/~1api~1v1~1images/post");
        JsonNode replace = spec.at("/paths/~1api~1v1~1images~1{imageId}/put");

        assertThat(upload.at("/requestBody/content").fieldNames()).toIterable().containsExactly("multipart/form-data");
        assertThat(upload.at("/requestBody/content/multipart~1form-data/schema/properties").has("files")).isTrue();
        assertThat(parameter(upload, "files").isMissingNode()).as("files in the query string").isTrue();
        assertThat(replace.at("/requestBody/content").fieldNames()).toIterable().containsExactly("multipart/form-data");
    }

    /** The operation's parameter of that name, or a missing node. */
    private static JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return MissingNode.getInstance();
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
