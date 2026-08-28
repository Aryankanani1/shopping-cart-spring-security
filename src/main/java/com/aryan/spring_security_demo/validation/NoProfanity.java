package com.aryan.spring_security_demo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** The constraint annotation. It points to the validator that does the work. */
@Documented
@Constraint(validatedBy = NoProfanityValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface NoProfanity {
    String message() default "Text contains disallowed words";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
