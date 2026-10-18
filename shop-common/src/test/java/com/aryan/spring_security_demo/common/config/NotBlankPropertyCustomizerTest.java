package com.aryan.spring_security_demo.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A {@code @NotBlank} string is documented with a pattern that rules out an empty or blank one. */
class NotBlankPropertyCustomizerTest {

    private final NotBlankPropertyCustomizer customizer = new NotBlankPropertyCustomizer();

    /** Fields to take the annotations of, as swagger-core passes them. */
    static class Request {
        @NotBlank @Size(max = 255) String name;
        @NotBlank String reason;
        @NotBlank @Pattern(regexp = "^[a-z]+$") String code;
        @Size(max = 255) String note;
        @NotNull Integer quantity;
    }

    @Test
    void thePattern_acceptsExactlyTheStringsNotBlankAccepts() {
        java.util.regex.Pattern notBlank = java.util.regex.Pattern.compile(NotBlankPropertyCustomizer.NOT_BLANK);
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            String one = String.valueOf(c);
            assertThat(notBlank.matcher(one).find()).as("U+%04X", (int) c).isEqualTo(!one.isBlank());
        }
        assertThat(notBlank.matcher(" \t\u2003\n").find()).isFalse();
        assertThat(notBlank.matcher("  Books ").find()).isTrue();
        assertThat(notBlank.matcher("").find()).isFalse();
    }

    @Test
    void aNotBlankString_getsThePattern() {
        Schema<?> name = new StringSchema().maxLength(255);

        Schema<?> documented = customize(name, "name");

        assertThat(documented.getPattern()).isEqualTo(NotBlankPropertyCustomizer.NOT_BLANK);
        assertThat(documented.getMaxLength()).isEqualTo(255);
    }

    @Test
    void anOpenApi31String_isRecognisedByItsTypes() {
        Schema<?> reason = new JsonSchema().types(Set.of("string"));

        assertThat(customize(reason, "reason").getPattern()).isEqualTo(NotBlankPropertyCustomizer.NOT_BLANK);
    }

    @Test
    void aPatternOfItsOwn_isKept() {
        Schema<?> code = new StringSchema().pattern("^[a-z]+$");

        assertThat(customize(code, "code").getPattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    void otherProperties_areLeftAlone() {
        Schema<?> note = new StringSchema().maxLength(255);
        Schema<?> quantity = new IntegerSchema();

        assertThat(customize(note, "note").getPattern()).isNull();
        assertThat(customize(quantity, "quantity").getPattern()).isNull();
    }

    private Schema<?> customize(Schema<?> property, String field) {
        AnnotatedType type = new AnnotatedType(String.class);
        try {
            type.ctxAnnotations(Request.class.getDeclaredField(field).getAnnotations());
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(field, e);
        }
        return customizer.customize(property, type);
    }
}
