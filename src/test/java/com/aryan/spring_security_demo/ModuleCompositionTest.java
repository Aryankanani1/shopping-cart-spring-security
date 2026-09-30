package com.aryan.spring_security_demo;

import com.aryan.spring_security_demo.common.ModuleConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the modular component scanning described on
 * {@link SpringSecurityDemoApplication}: the application class lists its modules
 * instead of scanning, and each module scans only its own package.
 */
@SpringBootTest
@ActiveProfiles("test")
class ModuleCompositionTest {

    private static final String ROOT = SpringSecurityDemoApplication.class.getPackageName();

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("every component lives in a package that a listed module scans")
    void everyComponent_isCoveredByAModule() {
        List<String> modulePackages = modules().stream().map(Class::getPackageName).toList();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> uncovered = scanner.findCandidateComponents(ROOT).stream()
                .map(BeanDefinition::getBeanClassName)
                // The application class is registered directly as the primary source, not scanned.
                .filter(name -> !name.equals(SpringSecurityDemoApplication.class.getName()))
                .filter(name -> !name.startsWith(ModuleCompositionTest.class.getName()))  // the fixture below
                .filter(name -> modulePackages.stream().noneMatch(pkg -> name.startsWith(pkg + ".")))
                .toList();

        assertThat(uncovered)
                .as("components outside every module are never registered — move them into a module package")
                .isEmpty();
    }

    @Test
    @DisplayName("the application class imports its modules instead of scanning")
    void applicationClass_doesNotComponentScan() {
        assertThat(AnnotatedElementUtils.hasAnnotation(SpringSecurityDemoApplication.class, ComponentScan.class))
                .as("no app-wide scan (e.g. @SpringBootApplication) on the application class")
                .isFalse();
        assertThat(modules()).allSatisfy(module ->
                assertThat(AnnotatedElementUtils.hasAnnotation(module, ModuleConfiguration.class))
                        .as("%s is a @ModuleConfiguration", module.getSimpleName())
                        .isTrue());
    }

    @Test
    @DisplayName("a component outside every module is not picked up")
    void strayComponent_isNotRegistered() {
        // StrayComponent sits in the root package, which the old app-wide scan
        // covered and no module does.
        assertThat(context.getBeanNamesForType(StrayComponent.class)).isEmpty();
    }

    private static List<Class<?>> modules() {
        return Arrays.asList(SpringSecurityDemoApplication.class.getAnnotation(Import.class).value());
    }

    @Component
    static class StrayComponent {
    }
}
