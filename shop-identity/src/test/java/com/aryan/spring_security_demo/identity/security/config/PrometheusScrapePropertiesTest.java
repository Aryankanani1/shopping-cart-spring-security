package com.aryan.spring_security_demo.identity.security.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Turning scraping on needs a usable password; startup fails otherwise. */
class PrometheusScrapePropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void scrapingOff_needsNoPassword() {
        assertThat(validator.validate(properties(false, ""))).isEmpty();
    }

    @Test
    void scrapingOn_withoutPassword_isInvalid() {
        assertThat(validator.validate(properties(true, ""))).hasSize(1);
    }

    @Test
    void scrapingOn_withAShortPassword_isInvalid() {
        assertThat(validator.validate(properties(true, "a".repeat(15)))).hasSize(1);
    }

    @Test
    void scrapingOn_withPasswordOverBcryptsLimit_isInvalid() {
        assertThat(validator.validate(properties(true, "é".repeat(40)))).hasSize(1);  // 80 bytes
    }

    @Test
    void scrapingOn_withAGoodPassword_isValid() {
        assertThat(validator.validate(properties(true, "a".repeat(16)))).isEmpty();
    }

    private static PrometheusScrapeProperties properties(boolean enabled, String password) {
        PrometheusScrapeProperties properties = new PrometheusScrapeProperties();
        properties.setScrapeEnabled(enabled);
        properties.setPassword(password);
        return properties;
    }
}
