package com.aryan.spring_security_demo.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.Digits;
import org.springdoc.core.customizers.PropertyCustomizer;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;

/**
 * Documents a {@code @Digits} number in the generated spec, which swagger-core
 * leaves out: its largest value as {@code maximum}, and its decimals as
 * {@code multipleOf}. Without them the spec allowed any number for a price or a
 * refund's amount, and the staging fuzzing (Schemathesis) spent its requests on
 * ones the API rejects with a 400.
 *
 * <p>Only what isn't set already: swagger-core applies the annotations it knows
 * after this runs, so an explicit {@code @DecimalMax} still wins.
 */
// The raw Schema is springdoc's: PropertyCustomizer declares it without a type argument.
@SuppressWarnings("rawtypes")
final class DigitsPropertyCustomizer implements PropertyCustomizer {

    @Override
    public Schema customize(Schema property, AnnotatedType type) {
        Digits digits = digits(type);
        if (property == null || digits == null || !isNumber(property)) {
            return property;
        }
        if (property.getMaximum() == null) {
            property.setMaximum(largest(digits));
        }
        if (property.getMultipleOf() == null) {
            property.setMultipleOf(BigDecimal.ONE.movePointLeft(digits.fraction()));
        }
        return property;
    }

    /** The largest number with that many digits: 999999999999.99 for 12 and 2. */
    static BigDecimal largest(Digits digits) {
        return BigDecimal.TEN.pow(digits.integer()).subtract(BigDecimal.ONE.movePointLeft(digits.fraction()));
    }

    /** OpenAPI 3.1 schemas list their types; 3.0 ones have a single type. */
    private static boolean isNumber(Schema property) {
        return "number".equals(property.getType())
                || property.getTypes() != null && property.getTypes().contains("number");
    }

    private static Digits digits(AnnotatedType type) {
        Annotation[] annotations = type.getCtxAnnotations();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof Digits digits) {
                    return digits;
                }
            }
        }
        return null;
    }
}
