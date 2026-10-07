package com.aryan.spring_security_demo.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** The validator: returns true if the value is acceptable. */
public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {

    private static final Set<String> BANNED = Set.of("spam", "scam");

    /** Anything that isn't a letter or a digit separates words. */
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Let @NotBlank handle null/blank; a null here is "valid" for this rule.
        if (value == null) return true;
        // Whole words only: "scam" is banned, but "Scampi" is a shrimp.
        return Arrays.stream(WORD_SEPARATOR.split(value.toLowerCase(Locale.ROOT)))
                .noneMatch(BANNED::contains);
    }
}
