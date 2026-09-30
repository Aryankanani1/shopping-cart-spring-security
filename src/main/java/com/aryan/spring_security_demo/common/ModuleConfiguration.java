package com.aryan.spring_security_demo.common;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a module's entry point: a configuration class that scans its own package
 * (and sub-packages) for components and {@code @ConfigurationProperties}, and
 * nothing else. The application class lists the modules explicitly, so there is
 * no app-wide scan — a module is either imported, or none of its beans exist.
 *
 * <p>The {@link TypeExcludeFilter} matters for tests: it is how test slices such
 * as {@code @WebMvcTest} narrow scanning to the web layer, and how
 * {@code @TestConfiguration} classes stay out of the scan. It is the same filter
 * {@code @SpringBootApplication} applies to its own scan.
 *
 * <p>Repositories and entities are deliberately <em>not</em> declared per module
 * ({@code @EnableJpaRepositories} / {@code @EntityScan}): Boot auto-configures
 * them for the whole application, and that auto-configuration is switched off in
 * slices that have no database. Declaring them here would break those slices.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration(proxyBeanMethods = false)
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
@ConfigurationPropertiesScan
public @interface ModuleConfiguration {
}
