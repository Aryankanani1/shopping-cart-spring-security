package com.aryan.spring_security_demo.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A {@code @Digits} number is documented with its largest value and its decimals. */
class DigitsPropertyCustomizerTest {

    private final DigitsPropertyCustomizer customizer = new DigitsPropertyCustomizer();

    /** Fields to take the annotations of, as swagger-core passes them. */
    static class Request {
        @Positive @Digits(integer = 12, fraction = 2) BigDecimal price;
        @Digits(integer = 3, fraction = 0) BigDecimal count;
        @Positive BigDecimal weight;
        @Digits(integer = 5, fraction = 0) Integer quantity;
    }

    @Test
    void aDigitsNumber_getsItsLargestValueAndItsDecimals() {
        Schema<?> price = customize(new NumberSchema(), "price");

        assertThat(price.getMaximum()).isEqualByComparingTo("999999999999.99");
        assertThat(price.getMultipleOf()).isEqualByComparingTo("0.01");
    }

    @Test
    void noDecimals_isAMultipleOfOne() {
        Schema<?> count = customize(new JsonSchema().types(Set.of("number")), "count");

        assertThat(count.getMaximum()).isEqualByComparingTo("999");
        assertThat(count.getMultipleOf()).isEqualByComparingTo("1");
    }

    @Test
    void aLimitOfItsOwn_isKept() {
        Schema<?> price = customize(new NumberSchema().maximum(new BigDecimal("500")), "price");

        assertThat(price.getMaximum()).isEqualByComparingTo("500");
        assertThat(price.getMultipleOf()).isEqualByComparingTo("0.01");
    }

    @Test
    void otherProperties_areLeftAlone() {
        Schema<?> weight = customize(new NumberSchema(), "weight");
        Schema<?> quantity = customize(new IntegerSchema(), "quantity");

        assertThat(weight.getMaximum()).isNull();
        assertThat(weight.getMultipleOf()).isNull();
        assertThat(quantity.getMaximum()).isNull();
    }

    private Schema<?> customize(Schema<?> property, String field) {
        AnnotatedType type = new AnnotatedType(BigDecimal.class);
        try {
            type.ctxAnnotations(Request.class.getDeclaredField(field).getAnnotations());
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(field, e);
        }
        return customizer.customize(property, type);
    }
}
