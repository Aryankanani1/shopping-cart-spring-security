package com.aryan.spring_security_demo.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.customizers.PropertyCustomizer;

import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * Documents a {@code @NotBlank} string in the generated spec as what the API
 * accepts: at least one character that isn't white space.
 *
 * <p>swagger-core gives a {@code @NotBlank} string {@code minLength: 1}, then
 * applies {@code @Size} over it, whose default {@code min = 0} wins. Most request
 * fields have both, so the spec said they could be {@code ""} (or {@code "   "}),
 * and the staging fuzzing (Schemathesis), which generates what the spec allows,
 * spent its requests on bodies the API rejects with a 400. The property gets a
 * pattern that needs a character other than white space, which rules out both. Its
 * {@code minLength} can't be fixed here: {@code @Size} is applied after this runs.
 */
// The raw Schema is springdoc's: PropertyCustomizer declares it without a type argument.
@SuppressWarnings("rawtypes")
final class NotBlankPropertyCustomizer implements PropertyCustomizer {

    /**
     * Matches a string with a character that {@link String#isBlank()}, which
     * {@code @NotBlank} checks with, doesn't count as white space: anything but
     * those {@link Character#isWhitespace(char)} accepts. Unanchored, because a
     * JSON Schema pattern may match anywhere in the string.
     */
    static final String NOT_BLANK =
            "[^\\u0009-\\u000D\\u001C-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]";

    @Override
    public Schema customize(Schema property, AnnotatedType type) {
        if (property != null && property.getPattern() == null && isString(property) && isNotBlank(type)) {
            property.setPattern(NOT_BLANK);
        }
        return property;
    }

    /** OpenAPI 3.1 schemas list their types; 3.0 ones have a single type. */
    private static boolean isString(Schema property) {
        return "string".equals(property.getType())
                || property.getTypes() != null && property.getTypes().contains("string");
    }

    private static boolean isNotBlank(AnnotatedType type) {
        Annotation[] annotations = type.getCtxAnnotations();
        return annotations != null
                && Arrays.stream(annotations).anyMatch(annotation -> annotation instanceof NotBlank);
    }
}
