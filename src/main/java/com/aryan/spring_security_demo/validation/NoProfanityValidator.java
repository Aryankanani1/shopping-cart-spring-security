package com.aryan.spring_security_demo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/** The validator: returns true if the value is acceptable. */
public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {

    private static final Set<String> BANNED = Set.of("spam", "scam");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Let @NotBlank handle null/blank; a null here is "valid" for this rule.
        if (value == null) return true;
        String lower = value.toLowerCase();
        return BANNED.stream().noneMatch(lower::contains);
    }
}
