package com.aryan.spring_security_demo.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Banned words are matched as whole words, in any case, wherever they sit in the text. */
class NoProfanityValidatorTest {

    private final NoProfanityValidator validator = new NoProfanityValidator();

    @ParameterizedTest
    @ValueSource(strings = {"spam widget", "Big SCAM", "Spam", "spam-free kettle", "the scam."})
    void aBannedWord_isRejected(String name) {
        assertThat(validator.isValid(name, null)).isFalse();
    }

    // Regression: the check matched substrings, so these names were rejected.
    @ParameterizedTest
    @ValueSource(strings = {"Scampi Shrimp", "Escambray Coffee", "Spamalot Soundtrack"})
    void aWordThatOnlyContainsABannedOne_isAccepted(String name) {
        assertThat(validator.isValid(name, null)).isTrue();
    }
}
